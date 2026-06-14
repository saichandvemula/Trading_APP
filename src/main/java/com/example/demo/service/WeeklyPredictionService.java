package com.example.demo.service;

import com.example.demo.config.Nifty50Properties;
import com.example.demo.domain.AuthSessionEntity;
import com.example.demo.domain.SignalDirection;
import com.example.demo.dto.AdvancedTechnicalSummaryDto;
import com.example.demo.dto.HistoricalCandleDto;
import com.example.demo.dto.Nifty50WeeklyPredictionResponse;
import com.example.demo.dto.WeeklyPredictionResponse;
import com.example.demo.dto.WeeklyPredictionSummaryDto;
import com.example.demo.dto.WeeklyTradePlanDto;
import com.example.demo.exception.SmartApiException;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WeeklyPredictionService {

    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter SMART_API_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String ONE_DAY = "ONE_DAY";

    private final SmartApiClient smartApiClient;
    private final AuthService authService;
    private final Nifty50Properties nifty50Properties;

    public WeeklyPredictionService(
            SmartApiClient smartApiClient,
            AuthService authService,
            Nifty50Properties nifty50Properties
    ) {
        this.smartApiClient = smartApiClient;
        this.authService = authService;
        this.nifty50Properties = nifty50Properties;
    }

    public Nifty50WeeklyPredictionResponse predictNifty50() {
        List<Nifty50Properties.Stock> stocks = nifty50Properties.nifty50();
        if (stocks == null || stocks.isEmpty()) {
            throw new SmartApiException("Configure trading.universe.nifty50 with stock symbol/token values");
        }

        List<WeeklyPredictionSummaryDto> results = new ArrayList<>();
        for (Nifty50Properties.Stock stock : stocks) {
            String symbol = normalize(stock.symbol());
            String exchange = normalizeExchange(stock.exchange());
            String token = stock.token();
            try {
                results.add(WeeklyPredictionSummaryDto.success(predict(symbol, exchange, token)));
            } catch (RuntimeException ex) {
                results.add(WeeklyPredictionSummaryDto.failed(symbol, exchange, token, ex.getMessage()));
            }
        }

        int success = (int) results.stream().filter(result -> "SUCCESS".equals(result.status())).count();
        return new Nifty50WeeklyPredictionResponse(
                Instant.now(),
                results.size(),
                success,
                results.size() - success,
                results
        );
    }

    @Transactional(readOnly = true)
    public WeeklyPredictionResponse predict(String stockName, String exchange, String requestedSymbolToken) {
        String normalizedStockName = normalize(stockName);
        String normalizedExchange = normalizeExchange(exchange);
        String symbolToken = resolveSymbolToken(normalizedStockName, requestedSymbolToken);

        LocalDate toDate = LocalDate.now(MARKET_ZONE);
        LocalDate fromDate = toDate.minusDays(60);
        String fromDateTime = fromDate.atTime(LocalTime.of(9, 15)).format(SMART_API_DATE_TIME);
        String toDateTime = toDate.atTime(LocalTime.of(15, 30)).format(SMART_API_DATE_TIME);

        AuthSessionEntity session = authService.requireSession();
        JsonNode response = smartApiClient.candleData(
                session.getJwtToken(),
                normalizedExchange,
                symbolToken,
                ONE_DAY,
                fromDateTime,
                toDateTime
        );

        List<HistoricalCandleDto> candles = parseCandles(response.path("data")).stream()
                .sorted(Comparator.comparing(HistoricalCandleDto::timestamp))
                .toList();
        if (candles.size() < 20) {
            throw new SmartApiException("Not enough historical candles available for weekly prediction");
        }

        List<HistoricalCandleDto> lastWeek = candles.size() > 5 ? candles.subList(candles.size() - 5, candles.size()) : candles;
        return score(normalizedStockName, normalizedExchange, symbolToken, fromDate, toDate, candles, lastWeek);
    }

    private WeeklyPredictionResponse score(
            String stockName,
            String exchange,
            String symbolToken,
            LocalDate fromDate,
            LocalDate toDate,
            List<HistoricalCandleDto> allCandles,
            List<HistoricalCandleDto> candles
    ) {
        HistoricalCandleDto first = candles.get(0);
        HistoricalCandleDto last = candles.get(candles.size() - 1);

        BigDecimal weeklyChange = percentChange(first.open(), last.close());
        BigDecimal ema5 = ema(allCandles, 5);
        BigDecimal ema20 = ema(allCandles, 20);
        BigDecimal rsi14 = rsi(allCandles, 14);
        MacdValues macdValues = macd(allCandles);
        BigDecimal atr14 = atr(allCandles, 14);
        BigDecimal atrPercent = percentOf(atr14, last.close());
        BigDecimal support = lowestLow(candles);
        BigDecimal resistance = highestHigh(candles);
        BigDecimal closePositionPercent = closePosition(last.close(), support, resistance);
        BigDecimal averageVolume = averageVolume(candles);
        BigDecimal latestVolumeRatio = volumeRatio(last.volume(), averageVolume);
        List<CandlestickPattern> candlestickPatterns = detectCandlestickPatterns(candles);

        int greenDays = 0;
        int redDays = 0;
        for (HistoricalCandleDto candle : candles) {
            int closeVsOpen = candle.close().compareTo(candle.open());
            if (closeVsOpen > 0) {
                greenDays++;
            } else if (closeVsOpen < 0) {
                redDays++;
            }
        }

        BigDecimal closeLocation = closeLocation(last);
        int score = 0;
        List<String> reasons = new ArrayList<>();

        if (last.close().compareTo(ema20) > 0 && ema5.compareTo(ema20) > 0) {
            score += 25;
            reasons.add("price is above EMA20 and EMA5 is above EMA20");
        } else if (last.close().compareTo(ema20) < 0 && ema5.compareTo(ema20) < 0) {
            score -= 25;
            reasons.add("price is below EMA20 and EMA5 is below EMA20");
        } else {
            reasons.add("EMA trend is mixed");
        }

        if (rsi14.compareTo(new BigDecimal("55")) >= 0 && rsi14.compareTo(new BigDecimal("70")) <= 0) {
            score += 18;
            reasons.add("RSI14 shows bullish momentum without overbought pressure");
        } else if (rsi14.compareTo(new BigDecimal("30")) >= 0 && rsi14.compareTo(new BigDecimal("45")) <= 0) {
            score -= 18;
            reasons.add("RSI14 shows bearish momentum without oversold bounce confirmation");
        } else if (rsi14.compareTo(new BigDecimal("70")) > 0) {
            score += 5;
            reasons.add("RSI14 is overbought, so bullish continuation needs caution");
        } else if (rsi14.compareTo(new BigDecimal("30")) < 0) {
            score -= 5;
            reasons.add("RSI14 is oversold, so bearish continuation needs caution");
        }

        if (macdValues.macd().compareTo(macdValues.signal()) > 0 && macdValues.histogram().compareTo(BigDecimal.ZERO) > 0) {
            score += 18;
            reasons.add("MACD is above signal with positive histogram");
        } else if (macdValues.macd().compareTo(macdValues.signal()) < 0 && macdValues.histogram().compareTo(BigDecimal.ZERO) < 0) {
            score -= 18;
            reasons.add("MACD is below signal with negative histogram");
        }

        if (weeklyChange.compareTo(new BigDecimal("1.00")) > 0) {
            score += 14;
            reasons.add("weekly close is more than 1% above the first open");
        } else if (weeklyChange.compareTo(new BigDecimal("-1.00")) < 0) {
            score -= 14;
            reasons.add("weekly close is more than 1% below the first open");
        } else {
            reasons.add("weekly change is muted");
        }

        if (greenDays >= redDays + 2) {
            score += 10;
            reasons.add("green sessions outnumber red sessions");
        } else if (redDays >= greenDays + 2) {
            score -= 10;
            reasons.add("red sessions outnumber green sessions");
        }

        if (closePositionPercent.compareTo(new BigDecimal("75")) >= 0) {
            score += 10;
            reasons.add("latest close is near weekly resistance breakout zone");
        } else if (closePositionPercent.compareTo(new BigDecimal("25")) <= 0) {
            score -= 10;
            reasons.add("latest close is near weekly support breakdown zone");
        }

        if (closeLocation.compareTo(new BigDecimal("0.65")) >= 0) {
            score += 7;
            reasons.add("latest candle closed near its high");
        } else if (closeLocation.compareTo(new BigDecimal("0.35")) <= 0) {
            score -= 7;
            reasons.add("latest candle closed near its low");
        }

        if (latestVolumeRatio.compareTo(new BigDecimal("1.20")) >= 0) {
            score += score >= 0 ? 8 : -8;
            reasons.add("latest volume is above recent average");
        } else if (latestVolumeRatio.compareTo(new BigDecimal("0.75")) <= 0) {
            reasons.add("latest volume is below recent average, reducing conviction");
        }

        int candlestickScore = candlestickScore(candlestickPatterns, closePositionPercent, latestVolumeRatio);
        if (candlestickScore != 0) {
            score += candlestickScore;
            reasons.add("candlestick pattern confirmation: " + patternNames(candlestickPatterns));
        } else if (!candlestickPatterns.isEmpty()) {
            reasons.add("candlestick patterns detected without strong directional confirmation: " + patternNames(candlestickPatterns));
        }

        if (atrPercent.compareTo(new BigDecimal("4.00")) > 0) {
            score = score * 85 / 100;
            reasons.add("ATR volatility is high, confidence adjusted lower");
        }

        int confidence = Math.min(Math.abs(score), 100);
        SignalDirection direction;
        if (confidence < 20) {
            direction = SignalDirection.SIDEWAYS;
        } else if (confidence < 45) {
            direction = SignalDirection.NO_TRADE;
        } else {
            direction = score > 0 ? SignalDirection.BULLISH : SignalDirection.BEARISH;
        }
        WeeklyTradePlanDto tradePlan = tradePlan(direction, confidence, last.close(), support, resistance, atr14);

        return new WeeklyPredictionResponse(
                stockName,
                exchange,
                symbolToken,
                fromDate,
                toDate,
                direction,
                confidence,
                weeklyChange,
                last.close(),
                new AdvancedTechnicalSummaryDto(
                        round(ema5),
                        round(ema20),
                        round(rsi14),
                        round(macdValues.macd()),
                        round(macdValues.signal()),
                        round(atr14),
                        round(atrPercent),
                        round(support),
                        round(resistance),
                        round(closePositionPercent),
                        round(averageVolume),
                        round(latestVolumeRatio),
                        candlestickPatterns.stream().map(pattern -> pattern.name).toList(),
                        trend(last.close(), ema5, ema20),
                        momentum(rsi14, macdValues),
                        volatility(atrPercent),
                        volumeSignal(latestVolumeRatio)
                ),
                tradePlan,
                String.join("; ", reasons),
                Instant.now(),
                candles
        );
    }

    private List<HistoricalCandleDto> parseCandles(JsonNode data) {
        if (!data.isArray()) {
            throw new SmartApiException("SmartAPI historical response did not include a data array");
        }
        List<HistoricalCandleDto> candles = new ArrayList<>();
        for (JsonNode row : data) {
            if (!row.isArray() || row.size() < 6) {
                continue;
            }
            candles.add(new HistoricalCandleDto(
                    OffsetDateTime.parse(row.get(0).asText()),
                    decimal(row.get(1)),
                    decimal(row.get(2)),
                    decimal(row.get(3)),
                    decimal(row.get(4)),
                    row.get(5).asLong()
            ));
        }
        return candles;
    }

    private BigDecimal decimal(JsonNode node) {
        return new BigDecimal(node.asText().replace(",", ""));
    }

    private BigDecimal ema(List<HistoricalCandleDto> candles, int period) {
        if (candles.size() < period) {
            throw new SmartApiException("Not enough candles to calculate EMA" + period);
        }
        BigDecimal multiplier = BigDecimal.valueOf(2)
                .divide(BigDecimal.valueOf(period + 1L), 8, RoundingMode.HALF_UP);
        BigDecimal ema = simpleAverageClose(candles.subList(0, period));
        for (int i = period; i < candles.size(); i++) {
            BigDecimal close = candles.get(i).close();
            ema = close.subtract(ema).multiply(multiplier).add(ema);
        }
        return ema;
    }

    private BigDecimal rsi(List<HistoricalCandleDto> candles, int period) {
        if (candles.size() <= period) {
            throw new SmartApiException("Not enough candles to calculate RSI" + period);
        }
        BigDecimal gain = BigDecimal.ZERO;
        BigDecimal loss = BigDecimal.ZERO;
        for (int i = 1; i <= period; i++) {
            BigDecimal change = candles.get(i).close().subtract(candles.get(i - 1).close());
            if (change.compareTo(BigDecimal.ZERO) > 0) {
                gain = gain.add(change);
            } else {
                loss = loss.add(change.abs());
            }
        }
        BigDecimal averageGain = gain.divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);
        BigDecimal averageLoss = loss.divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);
        for (int i = period + 1; i < candles.size(); i++) {
            BigDecimal change = candles.get(i).close().subtract(candles.get(i - 1).close());
            BigDecimal currentGain = change.compareTo(BigDecimal.ZERO) > 0 ? change : BigDecimal.ZERO;
            BigDecimal currentLoss = change.compareTo(BigDecimal.ZERO) < 0 ? change.abs() : BigDecimal.ZERO;
            averageGain = averageGain.multiply(BigDecimal.valueOf(period - 1L)).add(currentGain)
                    .divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);
            averageLoss = averageLoss.multiply(BigDecimal.valueOf(period - 1L)).add(currentLoss)
                    .divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);
        }
        if (averageLoss.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.valueOf(100);
        }
        BigDecimal relativeStrength = averageGain.divide(averageLoss, 8, RoundingMode.HALF_UP);
        return BigDecimal.valueOf(100).subtract(
                BigDecimal.valueOf(100).divide(BigDecimal.ONE.add(relativeStrength), 8, RoundingMode.HALF_UP)
        );
    }

    private MacdValues macd(List<HistoricalCandleDto> candles) {
        if (candles.size() < 35) {
            throw new SmartApiException("Not enough candles to calculate MACD");
        }
        List<BigDecimal> macdLine = new ArrayList<>();
        for (int i = 25; i < candles.size(); i++) {
            List<HistoricalCandleDto> slice = candles.subList(0, i + 1);
            macdLine.add(ema(slice, 12).subtract(ema(slice, 26)));
        }
        BigDecimal signal = emaValues(macdLine, 9);
        BigDecimal macd = macdLine.get(macdLine.size() - 1);
        return new MacdValues(macd, signal, macd.subtract(signal));
    }

    private BigDecimal atr(List<HistoricalCandleDto> candles, int period) {
        if (candles.size() <= period) {
            throw new SmartApiException("Not enough candles to calculate ATR" + period);
        }
        List<BigDecimal> trueRanges = new ArrayList<>();
        for (int i = 1; i < candles.size(); i++) {
            HistoricalCandleDto current = candles.get(i);
            BigDecimal previousClose = candles.get(i - 1).close();
            BigDecimal highLow = current.high().subtract(current.low()).abs();
            BigDecimal highClose = current.high().subtract(previousClose).abs();
            BigDecimal lowClose = current.low().subtract(previousClose).abs();
            trueRanges.add(highLow.max(highClose).max(lowClose));
        }
        BigDecimal atr = simpleAverage(trueRanges.subList(0, period));
        for (int i = period; i < trueRanges.size(); i++) {
            atr = atr.multiply(BigDecimal.valueOf(period - 1L)).add(trueRanges.get(i))
                    .divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);
        }
        return atr;
    }

    private BigDecimal simpleAverageClose(List<HistoricalCandleDto> candles) {
        return candles.stream()
                .map(HistoricalCandleDto::close)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(candles.size()), 8, RoundingMode.HALF_UP);
    }

    private BigDecimal simpleAverage(List<BigDecimal> values) {
        return values.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 8, RoundingMode.HALF_UP);
    }

    private BigDecimal emaValues(List<BigDecimal> values, int period) {
        BigDecimal multiplier = BigDecimal.valueOf(2)
                .divide(BigDecimal.valueOf(period + 1L), 8, RoundingMode.HALF_UP);
        BigDecimal ema = simpleAverage(values.subList(0, period));
        for (int i = period; i < values.size(); i++) {
            ema = values.get(i).subtract(ema).multiply(multiplier).add(ema);
        }
        return ema;
    }

    private BigDecimal closeLocation(HistoricalCandleDto candle) {
        BigDecimal range = candle.high().subtract(candle.low());
        if (range.compareTo(BigDecimal.ZERO) == 0) {
            return new BigDecimal("0.50");
        }
        return candle.close().subtract(candle.low()).divide(range, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal highestHigh(List<HistoricalCandleDto> candles) {
        return candles.stream()
                .map(HistoricalCandleDto::high)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal lowestLow(List<HistoricalCandleDto> candles) {
        return candles.stream()
                .map(HistoricalCandleDto::low)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal closePosition(BigDecimal close, BigDecimal support, BigDecimal resistance) {
        BigDecimal range = resistance.subtract(support);
        if (range.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.valueOf(50);
        }
        return close.subtract(support)
                .multiply(BigDecimal.valueOf(100))
                .divide(range, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal averageVolume(List<HistoricalCandleDto> candles) {
        return candles.stream()
                .map(candle -> BigDecimal.valueOf(candle.volume()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(candles.size()), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal volumeRatio(long latestVolume, BigDecimal averageVolume) {
        if (averageVolume.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(latestVolume).divide(averageVolume, 4, RoundingMode.HALF_UP);
    }

    private List<CandlestickPattern> detectCandlestickPatterns(List<HistoricalCandleDto> candles) {
        List<CandlestickPattern> patterns = new ArrayList<>();
        if (candles.isEmpty()) {
            return patterns;
        }

        HistoricalCandleDto latest = candles.get(candles.size() - 1);
        BigDecimal range = candleRange(latest);
        BigDecimal body = candleBody(latest);
        BigDecimal upperWick = upperWick(latest);
        BigDecimal lowerWick = lowerWick(latest);

        if (range.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal bodyRatio = body.divide(range, 4, RoundingMode.HALF_UP);
            if (bodyRatio.compareTo(new BigDecimal("0.10")) <= 0) {
                patterns.add(new CandlestickPattern("DOJI", 0));
            }
            if (lowerWick.compareTo(body.multiply(BigDecimal.valueOf(2))) >= 0
                    && upperWick.compareTo(body.max(range.multiply(new BigDecimal("0.10")))) <= 0) {
                patterns.add(new CandlestickPattern("HAMMER", 12));
            }
            if (upperWick.compareTo(body.multiply(BigDecimal.valueOf(2))) >= 0
                    && lowerWick.compareTo(body.max(range.multiply(new BigDecimal("0.10")))) <= 0) {
                patterns.add(new CandlestickPattern("SHOOTING_STAR", -12));
            }
            if (isBullish(latest) && bodyRatio.compareTo(new BigDecimal("0.75")) >= 0) {
                patterns.add(new CandlestickPattern("BULLISH_MARUBOZU", 10));
            }
            if (isBearish(latest) && bodyRatio.compareTo(new BigDecimal("0.75")) >= 0) {
                patterns.add(new CandlestickPattern("BEARISH_MARUBOZU", -10));
            }
        }

        if (candles.size() >= 2) {
            HistoricalCandleDto previous = candles.get(candles.size() - 2);
            if (isBearish(previous) && isBullish(latest)
                    && latest.open().compareTo(previous.close()) <= 0
                    && latest.close().compareTo(previous.open()) >= 0) {
                patterns.add(new CandlestickPattern("BULLISH_ENGULFING", 18));
            }
            if (isBullish(previous) && isBearish(latest)
                    && latest.open().compareTo(previous.close()) >= 0
                    && latest.close().compareTo(previous.open()) <= 0) {
                patterns.add(new CandlestickPattern("BEARISH_ENGULFING", -18));
            }
        }

        if (candles.size() >= 3) {
            HistoricalCandleDto first = candles.get(candles.size() - 3);
            HistoricalCandleDto middle = candles.get(candles.size() - 2);
            HistoricalCandleDto third = latest;
            BigDecimal firstMiddle = first.open().add(first.close()).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
            if (isBearish(first)
                    && isSmallBody(middle)
                    && isBullish(third)
                    && third.close().compareTo(firstMiddle) > 0) {
                patterns.add(new CandlestickPattern("MORNING_STAR", 20));
            }
            if (isBullish(first)
                    && isSmallBody(middle)
                    && isBearish(third)
                    && third.close().compareTo(firstMiddle) < 0) {
                patterns.add(new CandlestickPattern("EVENING_STAR", -20));
            }
        }

        return patterns;
    }

    private int candlestickScore(
            List<CandlestickPattern> patterns,
            BigDecimal closePositionPercent,
            BigDecimal latestVolumeRatio
    ) {
        int score = patterns.stream().mapToInt(CandlestickPattern::score).sum();
        if (score > 0 && closePositionPercent.compareTo(new BigDecimal("35")) <= 0) {
            score += 5;
        }
        if (score < 0 && closePositionPercent.compareTo(new BigDecimal("65")) >= 0) {
            score -= 5;
        }
        if (score != 0 && latestVolumeRatio.compareTo(new BigDecimal("1.20")) >= 0) {
            score += score > 0 ? 5 : -5;
        }
        if (patterns.stream().anyMatch(pattern -> "DOJI".equals(pattern.name))) {
            score = score * 70 / 100;
        }
        return Math.max(Math.min(score, 25), -25);
    }

    private String patternNames(List<CandlestickPattern> patterns) {
        return String.join(", ", patterns.stream().map(CandlestickPattern::name).toList());
    }

    private boolean isBullish(HistoricalCandleDto candle) {
        return candle.close().compareTo(candle.open()) > 0;
    }

    private boolean isBearish(HistoricalCandleDto candle) {
        return candle.close().compareTo(candle.open()) < 0;
    }

    private boolean isSmallBody(HistoricalCandleDto candle) {
        BigDecimal range = candleRange(candle);
        if (range.compareTo(BigDecimal.ZERO) == 0) {
            return true;
        }
        return candleBody(candle).divide(range, 4, RoundingMode.HALF_UP)
                .compareTo(new BigDecimal("0.30")) <= 0;
    }

    private BigDecimal candleBody(HistoricalCandleDto candle) {
        return candle.close().subtract(candle.open()).abs();
    }

    private BigDecimal candleRange(HistoricalCandleDto candle) {
        return candle.high().subtract(candle.low()).abs();
    }

    private BigDecimal upperWick(HistoricalCandleDto candle) {
        return candle.high().subtract(candle.open().max(candle.close())).max(BigDecimal.ZERO);
    }

    private BigDecimal lowerWick(HistoricalCandleDto candle) {
        return candle.open().min(candle.close()).subtract(candle.low()).max(BigDecimal.ZERO);
    }

    private BigDecimal percentOf(BigDecimal value, BigDecimal base) {
        if (base.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return value.multiply(BigDecimal.valueOf(100)).divide(base, 4, RoundingMode.HALF_UP);
    }

    private WeeklyTradePlanDto tradePlan(
            SignalDirection direction,
            int confidence,
            BigDecimal lastClose,
            BigDecimal support,
            BigDecimal resistance,
            BigDecimal atr
    ) {
        BigDecimal atrHalf = atr.multiply(new BigDecimal("0.50"));
        BigDecimal atrOne = atr;
        BigDecimal atrTwo = atr.multiply(new BigDecimal("2.00"));

        if (direction == SignalDirection.BULLISH) {
            BigDecimal entryAbove = resistance;
            BigDecimal stopLoss = support.max(lastClose.subtract(atrHalf));
            BigDecimal target1 = entryAbove.add(atrOne);
            BigDecimal target2 = entryAbove.add(atrTwo);
            return plan(
                    "BUY_ON_BREAKOUT",
                    entryAbove,
                    null,
                    stopLoss,
                    target1,
                    target2,
                    "Buy only if price sustains above resistance. Avoid chasing below the trigger."
            );
        }

        if (direction == SignalDirection.BEARISH) {
            BigDecimal entryBelow = support;
            BigDecimal stopLoss = resistance.min(lastClose.add(atrHalf));
            BigDecimal target1 = entryBelow.subtract(atrOne);
            BigDecimal target2 = entryBelow.subtract(atrTwo);
            return plan(
                    "SELL_ON_BREAKDOWN",
                    null,
                    entryBelow,
                    stopLoss,
                    target1,
                    target2,
                    "Sell only if price sustains below support. Avoid shorts above the breakdown trigger."
            );
        }

        String action = direction == SignalDirection.SIDEWAYS ? "RANGE_NO_TRADE" : "WAIT_FOR_CONFIRMATION";
        String note = confidence < 45
                ? "Signals are mixed. Wait for breakout above resistance or breakdown below support."
                : "Range-bound setup. Prefer no positional trade until direction expands.";
        return new WeeklyTradePlanDto(
                action,
                round(resistance),
                round(support),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                note
        );
    }

    private WeeklyTradePlanDto plan(
            String action,
            BigDecimal entryAbove,
            BigDecimal entryBelow,
            BigDecimal stopLoss,
            BigDecimal target1,
            BigDecimal target2,
            String note
    ) {
        BigDecimal entry = entryAbove != null ? entryAbove : entryBelow;
        BigDecimal risk = entry.subtract(stopLoss).abs();
        BigDecimal reward1 = target1.subtract(entry).abs();
        BigDecimal reward2 = target2.subtract(entry).abs();
        return new WeeklyTradePlanDto(
                action,
                roundOrNull(entryAbove),
                roundOrNull(entryBelow),
                round(stopLoss),
                round(target1),
                round(target2),
                round(risk),
                round(reward1),
                round(reward2),
                riskReward(reward1, risk),
                riskReward(reward2, risk),
                note
        );
    }

    private BigDecimal riskReward(BigDecimal reward, BigDecimal risk) {
        if (risk.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return reward.divide(risk, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal percentChange(BigDecimal from, BigDecimal to) {
        if (from == null || from.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return to.subtract(from)
                .multiply(BigDecimal.valueOf(100))
                .divide(from, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal round(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal roundOrNull(BigDecimal value) {
        return value == null ? null : round(value);
    }

    private String trend(BigDecimal close, BigDecimal ema5, BigDecimal ema20) {
        if (close.compareTo(ema20) > 0 && ema5.compareTo(ema20) > 0) {
            return "UPTREND";
        }
        if (close.compareTo(ema20) < 0 && ema5.compareTo(ema20) < 0) {
            return "DOWNTREND";
        }
        return "MIXED";
    }

    private String momentum(BigDecimal rsi14, MacdValues macdValues) {
        boolean macdBullish = macdValues.macd().compareTo(macdValues.signal()) > 0;
        if (rsi14.compareTo(new BigDecimal("55")) >= 0 && macdBullish) {
            return "BULLISH";
        }
        if (rsi14.compareTo(new BigDecimal("45")) <= 0 && !macdBullish) {
            return "BEARISH";
        }
        return "NEUTRAL";
    }

    private String volatility(BigDecimal atrPercent) {
        if (atrPercent.compareTo(new BigDecimal("4.00")) > 0) {
            return "HIGH";
        }
        if (atrPercent.compareTo(new BigDecimal("1.25")) < 0) {
            return "LOW";
        }
        return "NORMAL";
    }

    private String volumeSignal(BigDecimal latestVolumeRatio) {
        if (latestVolumeRatio.compareTo(new BigDecimal("1.20")) >= 0) {
            return "EXPANDING";
        }
        if (latestVolumeRatio.compareTo(new BigDecimal("0.75")) <= 0) {
            return "WEAK";
        }
        return "NORMAL";
    }

    private String resolveSymbolToken(String stockName, String requestedSymbolToken) {
        if (requestedSymbolToken != null && !requestedSymbolToken.isBlank()) {
            return requestedSymbolToken.trim();
        }
        return switch (stockName) {
            case "NIFTY" -> "99926000";
            case "BANKNIFTY" -> "99926009";
            default -> throw new SmartApiException("Provide symbolToken for " + stockName + " using ?symbolToken=...");
        };
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new SmartApiException("Stock name is required");
        }
        return value.trim().toUpperCase(Locale.ENGLISH);
    }

    private String normalizeExchange(String exchange) {
        return exchange == null || exchange.isBlank() ? "NSE" : exchange.trim().toUpperCase(Locale.ENGLISH);
    }

    private record MacdValues(BigDecimal macd, BigDecimal signal, BigDecimal histogram) {
    }

    private record CandlestickPattern(String name, int score) {
    }
}

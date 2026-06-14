package com.example.demo.dto;

import java.math.BigDecimal;

public record WeeklyTradePlanDto(
        String action,
        BigDecimal entryAbove,
        BigDecimal entryBelow,
        BigDecimal stopLoss,
        BigDecimal target1,
        BigDecimal target2,
        BigDecimal riskPoints,
        BigDecimal rewardToTarget1,
        BigDecimal rewardToTarget2,
        BigDecimal riskRewardTarget1,
        BigDecimal riskRewardTarget2,
        String plan
) {
}

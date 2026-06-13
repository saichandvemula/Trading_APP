alter table if exists market_ticks add column if not exists stock_name varchar(64);
update market_ticks set stock_name = instrument where stock_name is null and instrument is not null;

alter table if exists option_snapshots add column if not exists stock_name varchar(64);
update option_snapshots set stock_name = instrument where stock_name is null and instrument is not null;

alter table if exists trading_signals add column if not exists stock_name varchar(64);
update trading_signals set stock_name = instrument where stock_name is null and instrument is not null;

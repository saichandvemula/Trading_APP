alter table if exists market_ticks add column if not exists stock_name varchar(64);
update market_ticks set stock_name = instrument where stock_name is null and instrument is not null;
update market_ticks set instrument = stock_name where instrument is null and stock_name is not null;
alter table if exists market_ticks alter column instrument drop not null;

alter table if exists option_snapshots add column if not exists stock_name varchar(64);
update option_snapshots set stock_name = instrument where stock_name is null and instrument is not null;
update option_snapshots set instrument = stock_name where instrument is null and stock_name is not null;
alter table if exists option_snapshots alter column instrument drop not null;

alter table if exists trading_signals add column if not exists stock_name varchar(64);
update trading_signals set stock_name = instrument where stock_name is null and instrument is not null;
update trading_signals set instrument = stock_name where instrument is null and stock_name is not null;
alter table if exists trading_signals alter column instrument drop not null;

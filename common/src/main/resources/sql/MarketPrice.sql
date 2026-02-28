CREATE TABLE IF NOT EXISTS MarketPrice (
                                           id INTEGER PRIMARY KEY AUTOINCREMENT,
                                           marketid INTEGER NOT NULL,
                                           price INTEGER NOT NULL,
                                           low INTEGER NOT NULL,
                                           high INTEGER NOT NULL,
                                           time INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_marketprice_marketid_time ON MarketPrice (marketid, time);
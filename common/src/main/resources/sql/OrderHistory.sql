CREATE TABLE IF NOT EXISTS OrderHistory (
                                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                                            itemid INTEGER NOT NULL,
                                            accountid INTEGER NOT NULL,
                                            userid_one INTEGER NOT NULL,
                                            userid_two INTEGER NOT NULL,
                                            type INTEGER NOT NULL,
                                            amount INTEGER NOT NULL,
                                            price INTEGER NOT NULL,
                                            time DATETIME NOT NULL,
                                            intermarket_group_one INTEGER NOT NULL DEFAULT 0,
                                            intermarket_group_two INTEGER NOT NULL DEFAULT 0,
                                            order_id INTEGER,
                                            finish_time INTEGER,
                                            limit_price INTEGER,
                                            target_volume INTEGER,
                                            final_status INTEGER
);

CREATE INDEX IF NOT EXISTS idx_itemid_userid_time ON OrderHistory (itemid, accountid);
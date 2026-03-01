CREATE TABLE IF NOT EXISTS OrderHistory (
                                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                                            itemid INTEGER NOT NULL,
                                            userid_one INTEGER NOT NULL,
                                            userid_two INTEGER NOT NULL,
                                            type INTEGER NOT NULL,
                                            amount INTEGER NOT NULL,
                                            price INTEGER NOT NULL,
                                            time DATETIME NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_itemid_userid_time ON OrderHistory (itemid, userid_one, userid_two);
CREATE TABLE IF NOT EXISTS OrderHistory (
                                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                                            itemid INTEGER NOT NULL,
                                            userid BLOB NOT NULL,
                                            type INTEGER NOT NULL,
                                            amount INTEGER NOT NULL,
                                            price INTEGER NOT NULL,
                                            time DATETIME NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_itemid_userid_time ON OrderHistory (itemid, userid);
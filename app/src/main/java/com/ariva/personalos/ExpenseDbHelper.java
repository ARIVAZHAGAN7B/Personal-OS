package com.ariva.personalos;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ExpenseDbHelper extends SQLiteOpenHelper {
    public static final String DB_NAME = "personal_expenses.db";
    public static final int DB_VERSION = 6;

    public static final String TYPE_EXPENSE = "expense";
    public static final String TYPE_INCOME = "income";
    public static final String TYPE_BOTH = "both";
    public static final String TYPE_BORROWED = "borrowed";
    public static final String TYPE_LENDED = "lended";
    private static final String BORROW_LEND_CATEGORY = "Borrow / Lend";
    private static final String NON_RETURNED_CATEGORY = "Non Returned Spending";
    private static final String BORROW_LEND_EVENT_OPENED = "opened";
    private static final String BORROW_LEND_EVENT_COMPLETED = "completed";

    public ExpenseDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE bank_accounts (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "opening_balance INTEGER NOT NULL DEFAULT 0," +
                "created_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE categories (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "type TEXT NOT NULL," +
                "created_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE transactions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "amount INTEGER NOT NULL," +
                "type TEXT NOT NULL," +
                "category_id INTEGER NOT NULL," +
                "bank_account_id INTEGER NOT NULL," +
                "transaction_date INTEGER NOT NULL," +
                "created_at INTEGER NOT NULL," +
                "borrow_lend_id INTEGER," +
                "borrow_lend_event TEXT," +
                "FOREIGN KEY(category_id) REFERENCES categories(id)," +
                "FOREIGN KEY(bank_account_id) REFERENCES bank_accounts(id))");

        db.execSQL("CREATE INDEX idx_transactions_date ON transactions(transaction_date)");
        db.execSQL("CREATE INDEX idx_transactions_type ON transactions(type)");
        db.execSQL("CREATE INDEX idx_transactions_account ON transactions(bank_account_id)");
        db.execSQL("CREATE UNIQUE INDEX idx_transactions_borrow_lend_event " +
                "ON transactions(borrow_lend_id, borrow_lend_event)");
        createBorrowLendTable(db);
        createNonReturnedSpendingTable(db);
        ensureCashAccount(db);

        long now = System.currentTimeMillis();
        seedCategory(db, "Food", TYPE_EXPENSE, now);
        seedCategory(db, "Travel", TYPE_EXPENSE, now);
        seedCategory(db, "Bills", TYPE_EXPENSE, now);
        seedCategory(db, "Salary", TYPE_INCOME, now);
        seedCategory(db, "Freelance", TYPE_INCOME, now);
        seedCategory(db, BORROW_LEND_CATEGORY, TYPE_BOTH, now);
        seedCategory(db, NON_RETURNED_CATEGORY, TYPE_EXPENSE, now);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            createBorrowLendTable(db);
        }
        if (oldVersion < 3) {
            ensureCashAccount(db);
        }
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN borrow_lend_id INTEGER");
            db.execSQL("ALTER TABLE transactions ADD COLUMN borrow_lend_event TEXT");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_transactions_borrow_lend_event " +
                    "ON transactions(borrow_lend_id, borrow_lend_event)");
            migrateBorrowLendTransactions(db);
        }
        if (oldVersion < 6) {
            createNonReturnedSpendingTable(db);
            ensureNonReturnedCategory(db);
        }
    }

    private void createBorrowLendTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS borrow_lend (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "amount INTEGER NOT NULL," +
                "type TEXT NOT NULL," +
                "bank_account_id INTEGER NOT NULL," +
                "is_completed INTEGER NOT NULL DEFAULT 0," +
                "created_at INTEGER NOT NULL," +
                "completed_at INTEGER," +
                "FOREIGN KEY(bank_account_id) REFERENCES bank_accounts(id))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_borrow_lend_account ON borrow_lend(bank_account_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_borrow_lend_status ON borrow_lend(is_completed)");
    }

    private void createNonReturnedSpendingTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS non_returned_spending (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "amount INTEGER NOT NULL," +
                "bank_account_id INTEGER," +
                "transaction_id INTEGER," +
                "created_at INTEGER NOT NULL," +
                "FOREIGN KEY(bank_account_id) REFERENCES bank_accounts(id)," +
                "FOREIGN KEY(transaction_id) REFERENCES transactions(id))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_non_returned_spending_created " +
                "ON non_returned_spending(created_at)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_non_returned_spending_account " +
                "ON non_returned_spending(bank_account_id)");
    }

    private void ensureCashAccount(SQLiteDatabase db) {
        Cursor cursor = db.rawQuery("SELECT id FROM bank_accounts WHERE lower(name) = 'cash' LIMIT 1", null);
        try {
            if (cursor.moveToFirst()) {
                return;
            }
        } finally {
            cursor.close();
        }

        ContentValues values = new ContentValues();
        values.put("name", "Cash");
        values.put("opening_balance", 0);
        values.put("created_at", System.currentTimeMillis());
        db.insert("bank_accounts", null, values);
    }

    private void seedCategory(SQLiteDatabase db, String name, String type, long now) {
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("type", type);
        values.put("created_at", now);
        db.insert("categories", null, values);
    }

    private long ensureBorrowLendCategory(SQLiteDatabase db) {
        Cursor cursor = db.rawQuery(
                "SELECT id FROM categories WHERE name = ? AND type = ? LIMIT 1",
                new String[]{BORROW_LEND_CATEGORY, TYPE_BOTH});
        try {
            if (cursor.moveToFirst()) {
                return cursor.getLong(0);
            }
        } finally {
            cursor.close();
        }

        ContentValues values = new ContentValues();
        values.put("name", BORROW_LEND_CATEGORY);
        values.put("type", TYPE_BOTH);
        values.put("created_at", System.currentTimeMillis());
        return db.insertOrThrow("categories", null, values);
    }

    private long ensureNonReturnedCategory(SQLiteDatabase db) {
        Cursor cursor = db.rawQuery(
                "SELECT id FROM categories WHERE name = ? AND (type = ? OR type = ?) LIMIT 1",
                new String[]{NON_RETURNED_CATEGORY, TYPE_EXPENSE, TYPE_BOTH});
        try {
            if (cursor.moveToFirst()) {
                return cursor.getLong(0);
            }
        } finally {
            cursor.close();
        }

        ContentValues values = new ContentValues();
        values.put("name", NON_RETURNED_CATEGORY);
        values.put("type", TYPE_EXPENSE);
        values.put("created_at", System.currentTimeMillis());
        return db.insertOrThrow("categories", null, values);
    }

    private void migrateBorrowLendTransactions(SQLiteDatabase db) {
        Cursor cursor = db.rawQuery(
                "SELECT id, name, amount, type, bank_account_id, is_completed, created_at, completed_at " +
                        "FROM borrow_lend ORDER BY id",
                null);
        try {
            while (cursor.moveToNext()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
                String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                long amount = cursor.getLong(cursor.getColumnIndexOrThrow("amount"));
                String type = cursor.getString(cursor.getColumnIndexOrThrow("type"));
                long accountId = cursor.getLong(cursor.getColumnIndexOrThrow("bank_account_id"));
                long createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"));
                insertBorrowLendTransaction(db, id, name, amount, type, accountId,
                        BORROW_LEND_EVENT_OPENED, createdAt);

                if (cursor.getInt(cursor.getColumnIndexOrThrow("is_completed")) == 1) {
                    int completedAtColumn = cursor.getColumnIndexOrThrow("completed_at");
                    long completedAt = cursor.isNull(completedAtColumn)
                            ? createdAt
                            : cursor.getLong(completedAtColumn);
                    insertBorrowLendTransaction(db, id, name, amount, type, accountId,
                            BORROW_LEND_EVENT_COMPLETED, completedAt);
                }
            }
        } finally {
            cursor.close();
        }
    }

    private void insertBorrowLendTransaction(SQLiteDatabase db, long borrowLendId, String person,
                                             long amount, String borrowLendType, long bankAccountId,
                                             String event, long date) {
        boolean opened = BORROW_LEND_EVENT_OPENED.equals(event);
        boolean borrowed = TYPE_BORROWED.equals(borrowLendType);
        String transactionType;
        String transactionName;
        if (opened && borrowed) {
            transactionType = TYPE_INCOME;
            transactionName = "Borrowed from " + person;
        } else if (opened) {
            transactionType = TYPE_EXPENSE;
            transactionName = "Lent to " + person;
        } else if (borrowed) {
            transactionType = TYPE_EXPENSE;
            transactionName = "Repaid " + person;
        } else {
            transactionType = TYPE_INCOME;
            transactionName = "Received from " + person;
        }

        ContentValues values = new ContentValues();
        values.put("name", transactionName);
        values.put("amount", amount);
        values.put("type", transactionType);
        values.put("category_id", ensureBorrowLendCategory(db));
        values.put("bank_account_id", bankAccountId);
        values.put("transaction_date", date);
        values.put("created_at", date);
        values.put("borrow_lend_id", borrowLendId);
        values.put("borrow_lend_event", event);
        db.insertOrThrow("transactions", null, values);
    }

    public long addBankAccount(String name, long openingBalance) {
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("opening_balance", openingBalance);
        values.put("created_at", System.currentTimeMillis());
        return getWritableDatabase().insert("bank_accounts", null, values);
    }

    public void updateBankAccount(long id, String name, long openingBalance) {
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("opening_balance", openingBalance);
        getWritableDatabase().update("bank_accounts", values, "id = ?", new String[]{String.valueOf(id)});
    }

    public long addCategory(String name, String type) {
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("type", type);
        values.put("created_at", System.currentTimeMillis());
        return getWritableDatabase().insert("categories", null, values);
    }

    public void updateCategory(long id, String name, String type) {
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("type", type);
        getWritableDatabase().update("categories", values, "id = ?", new String[]{String.valueOf(id)});
    }

    public long addTransaction(String name, long amount, String type, long categoryId, long bankAccountId, long date) {
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("amount", amount);
        values.put("type", type);
        values.put("category_id", categoryId);
        values.put("bank_account_id", bankAccountId);
        values.put("transaction_date", date);
        values.put("created_at", System.currentTimeMillis());
        return getWritableDatabase().insert("transactions", null, values);
    }

    public void updateTransaction(long id, String name, long amount, String type, long categoryId, long bankAccountId, long date) {
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("amount", amount);
        values.put("type", type);
        values.put("category_id", categoryId);
        values.put("bank_account_id", bankAccountId);
        values.put("transaction_date", date);
        getWritableDatabase().update("transactions", values, "id = ?", new String[]{String.valueOf(id)});
    }

    public long addBorrowLend(String name, long amount, String type, long bankAccountId) {
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        db.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            values.put("name", name);
            values.put("amount", amount);
            values.put("type", type);
            values.put("bank_account_id", bankAccountId);
            values.put("is_completed", 0);
            values.put("created_at", now);
            long id = db.insertOrThrow("borrow_lend", null, values);
            insertBorrowLendTransaction(db, id, name, amount, type, bankAccountId,
                    BORROW_LEND_EVENT_OPENED, now);
            db.setTransactionSuccessful();
            return id;
        } finally {
            db.endTransaction();
        }
    }

    public void completeBorrowLend(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                    "SELECT name, amount, type, bank_account_id FROM borrow_lend " +
                            "WHERE id = ? AND is_completed = 0",
                    new String[]{String.valueOf(id)});
            if (!cursor.moveToFirst()) {
                db.setTransactionSuccessful();
                return;
            }

            String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
            long amount = cursor.getLong(cursor.getColumnIndexOrThrow("amount"));
            String type = cursor.getString(cursor.getColumnIndexOrThrow("type"));
            long bankAccountId = cursor.getLong(cursor.getColumnIndexOrThrow("bank_account_id"));
            long now = System.currentTimeMillis();
            insertBorrowLendTransaction(db, id, name, amount, type, bankAccountId,
                    BORROW_LEND_EVENT_COMPLETED, now);

            ContentValues values = new ContentValues();
            values.put("is_completed", 1);
            values.put("completed_at", now);
            db.update("borrow_lend", values, "id = ? AND is_completed = 0",
                    new String[]{String.valueOf(id)});
            db.setTransactionSuccessful();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            db.endTransaction();
        }
    }

    public long addNonReturnedSpending(String name, long amount, long bankAccountId) {
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        db.beginTransaction();
        try {
            Long transactionId = null;
            if (bankAccountId > 0) {
                ContentValues transaction = new ContentValues();
                transaction.put("name", "Non returned spending - " + name);
                transaction.put("amount", amount);
                transaction.put("type", TYPE_EXPENSE);
                transaction.put("category_id", ensureNonReturnedCategory(db));
                transaction.put("bank_account_id", bankAccountId);
                transaction.put("transaction_date", now);
                transaction.put("created_at", now);
                transactionId = db.insertOrThrow("transactions", null, transaction);
            }

            ContentValues values = new ContentValues();
            values.put("name", name);
            values.put("amount", amount);
            if (bankAccountId > 0) {
                values.put("bank_account_id", bankAccountId);
            }
            if (transactionId != null) {
                values.put("transaction_id", transactionId);
            }
            values.put("created_at", now);
            long id = db.insertOrThrow("non_returned_spending", null, values);
            db.setTransactionSuccessful();
            return id;
        } finally {
            db.endTransaction();
        }
    }

    public void deleteNonReturnedSpending(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                    "SELECT transaction_id FROM non_returned_spending WHERE id = ?",
                    new String[]{String.valueOf(id)});
            if (cursor.moveToFirst()) {
                int transactionColumn = cursor.getColumnIndexOrThrow("transaction_id");
                if (!cursor.isNull(transactionColumn)) {
                    db.delete("transactions", "id = ?",
                            new String[]{String.valueOf(cursor.getLong(transactionColumn))});
                }
            }
            db.delete("non_returned_spending", "id = ?", new String[]{String.valueOf(id)});
            db.setTransactionSuccessful();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            db.endTransaction();
        }
    }

    public void deleteTransaction(long id) {
        getWritableDatabase().delete("transactions", "id = ?", new String[]{String.valueOf(id)});
    }

    public Cursor getTransaction(long id) {
        return getReadableDatabase().rawQuery(
                "SELECT id, name, amount, type, category_id, bank_account_id, transaction_date, borrow_lend_id " +
                        "FROM transactions WHERE id = ?",
                new String[]{String.valueOf(id)});
    }

    public Cursor getAccountsWithBalances() {
        return getReadableDatabase().rawQuery(
                "SELECT a.id, a.name, a.opening_balance, a.opening_balance + " +
                        "COALESCE(t.transaction_total, 0) AS balance " +
                        "FROM bank_accounts a " +
                        "LEFT JOIN (" +
                        "SELECT bank_account_id, SUM(CASE WHEN type = 'income' THEN amount ELSE -amount END) AS transaction_total " +
                        "FROM transactions GROUP BY bank_account_id" +
                        ") t ON t.bank_account_id = a.id " +
                        "ORDER BY a.name", null);
    }

    public Cursor getBankAccount(long id) {
        return getReadableDatabase().rawQuery(
                "SELECT id, name, opening_balance FROM bank_accounts WHERE id = ?",
                new String[]{String.valueOf(id)});
    }

    public Cursor getCategoriesForType(String type) {
        return getReadableDatabase().rawQuery(
                "SELECT id, name, type FROM categories WHERE type = ? OR type = ? ORDER BY name",
                new String[]{type, TYPE_BOTH});
    }

    public Cursor getAllCategories() {
        return getReadableDatabase().rawQuery(
                "SELECT id, name, type FROM categories ORDER BY type, name", null);
    }

    public Cursor getCategory(long id) {
        return getReadableDatabase().rawQuery(
                "SELECT id, name, type FROM categories WHERE id = ?",
                new String[]{String.valueOf(id)});
    }

    public Cursor getRecentTransactions(int limit) {
        return getReadableDatabase().rawQuery(
                "SELECT t.id, t.name, t.amount, t.type, t.transaction_date, c.name AS category, a.name AS account " +
                        "FROM transactions t " +
                        "JOIN categories c ON c.id = t.category_id " +
                        "JOIN bank_accounts a ON a.id = t.bank_account_id " +
                        "ORDER BY t.transaction_date DESC, t.created_at DESC LIMIT ?",
                new String[]{String.valueOf(limit)});
    }

    public Cursor getFilteredTransactions(String type, long accountId, long categoryId, long startInclusive,
                                          long endExclusive, String searchText, String sortOrder, int limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT t.id, t.name, t.amount, t.type, t.transaction_date, c.name AS category, a.name AS account " +
                        "FROM transactions t " +
                        "JOIN categories c ON c.id = t.category_id " +
                        "JOIN bank_accounts a ON a.id = t.bank_account_id WHERE 1 = 1");
        java.util.ArrayList<String> args = new java.util.ArrayList<>();

        if (!"all".equals(type)) {
            sql.append(" AND t.type = ?");
            args.add(type);
        }
        if (accountId > 0) {
            sql.append(" AND t.bank_account_id = ?");
            args.add(String.valueOf(accountId));
        }
        if (categoryId > 0) {
            sql.append(" AND t.category_id = ?");
            args.add(String.valueOf(categoryId));
        }
        if (startInclusive > 0 && endExclusive > 0) {
            sql.append(" AND t.transaction_date >= ? AND t.transaction_date < ?");
            args.add(String.valueOf(startInclusive));
            args.add(String.valueOf(endExclusive));
        }
        if (searchText != null && !searchText.trim().isEmpty()) {
            sql.append(" AND t.name LIKE ?");
            args.add("%" + searchText.trim() + "%");
        }

        if ("amount_high".equals(sortOrder)) {
            sql.append(" ORDER BY t.amount DESC");
        } else if ("amount_low".equals(sortOrder)) {
            sql.append(" ORDER BY t.amount ASC");
        } else if ("oldest".equals(sortOrder)) {
            sql.append(" ORDER BY t.transaction_date ASC, t.created_at ASC");
        } else {
            sql.append(" ORDER BY t.transaction_date DESC, t.created_at DESC");
        }
        sql.append(" LIMIT ?");
        args.add(String.valueOf(limit));

        return getReadableDatabase().rawQuery(sql.toString(), args.toArray(new String[0]));
    }

    public Cursor getTransactionsBetween(long startInclusive, long endExclusive) {
        return getReadableDatabase().rawQuery(
                "SELECT t.id, t.name, t.amount, t.type, t.transaction_date, t.created_at, " +
                        "COALESCE(c.name, 'Missing category') AS category, " +
                        "COALESCE(a.name, 'Missing account') AS account " +
                        "FROM transactions t " +
                        "LEFT JOIN categories c ON c.id = t.category_id " +
                        "LEFT JOIN bank_accounts a ON a.id = t.bank_account_id " +
                        "WHERE t.transaction_date >= ? AND t.transaction_date < ? " +
                        "ORDER BY t.transaction_date DESC, t.created_at DESC",
                new String[]{String.valueOf(startInclusive), String.valueOf(endExclusive)});
    }

    public Map<String, Long> getIncomeExpenseSummary(long startInclusive, long endExclusive) {
        HashMap<String, Long> summary = new HashMap<>();
        summary.put("income", 0L);
        summary.put("expense", 0L);
        summary.put("transaction_count", 0L);
        summary.put("income_count", 0L);
        summary.put("expense_count", 0L);

        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT " +
                        "COALESCE(SUM(CASE WHEN type = 'income' THEN amount ELSE 0 END), 0) AS income_total, " +
                        "COALESCE(SUM(CASE WHEN type = 'expense' THEN amount ELSE 0 END), 0) AS expense_total, " +
                        "COUNT(*) AS transaction_count, " +
                        "COALESCE(SUM(CASE WHEN type = 'income' THEN 1 ELSE 0 END), 0) AS income_count, " +
                        "COALESCE(SUM(CASE WHEN type = 'expense' THEN 1 ELSE 0 END), 0) AS expense_count " +
                        "FROM transactions WHERE transaction_date >= ? AND transaction_date < ?",
                new String[]{String.valueOf(startInclusive), String.valueOf(endExclusive)});
        try {
            if (cursor.moveToFirst()) {
                summary.put("income", cursor.getLong(cursor.getColumnIndexOrThrow("income_total")));
                summary.put("expense", cursor.getLong(cursor.getColumnIndexOrThrow("expense_total")));
                summary.put("transaction_count", cursor.getLong(cursor.getColumnIndexOrThrow("transaction_count")));
                summary.put("income_count", cursor.getLong(cursor.getColumnIndexOrThrow("income_count")));
                summary.put("expense_count", cursor.getLong(cursor.getColumnIndexOrThrow("expense_count")));
            }
        } finally {
            cursor.close();
        }
        return summary;
    }

    public Cursor getCategoryBreakdown(String type, long startInclusive, long endExclusive) {
        return getReadableDatabase().rawQuery(
                "SELECT c.id, COALESCE(c.name, 'Missing category') AS name, COALESCE(SUM(t.amount), 0) AS total " +
                        "FROM transactions t " +
                        "LEFT JOIN categories c ON c.id = t.category_id " +
                        "WHERE t.type = ? AND t.transaction_date >= ? AND t.transaction_date < ? " +
                        "GROUP BY t.category_id, c.name ORDER BY total DESC",
                new String[]{type, String.valueOf(startInclusive), String.valueOf(endExclusive)});
    }

    public Cursor getAccountActivity(long startInclusive, long endExclusive) {
        return getReadableDatabase().rawQuery(
                "SELECT balances.id, balances.name, balances.balance, " +
                        "COALESCE(activity.income_total, 0) AS income_total, " +
                        "COALESCE(activity.expense_total, 0) AS expense_total " +
                        "FROM (" +
                        "SELECT a.id, a.name, a.opening_balance + " +
                        "COALESCE(t.transaction_total, 0) AS balance " +
                        "FROM bank_accounts a " +
                        "LEFT JOIN (" +
                        "SELECT bank_account_id, SUM(CASE WHEN type = 'income' THEN amount ELSE -amount END) AS transaction_total " +
                        "FROM transactions GROUP BY bank_account_id" +
                        ") t ON t.bank_account_id = a.id" +
                        ") balances " +
                        "LEFT JOIN (" +
                        "SELECT bank_account_id, " +
                        "COALESCE(SUM(CASE WHEN type = 'income' THEN amount ELSE 0 END), 0) AS income_total, " +
                        "COALESCE(SUM(CASE WHEN type = 'expense' THEN amount ELSE 0 END), 0) AS expense_total " +
                        "FROM transactions WHERE transaction_date >= ? AND transaction_date < ? " +
                        "GROUP BY bank_account_id" +
                        ") activity ON activity.bank_account_id = balances.id " +
                        "ORDER BY (COALESCE(activity.income_total, 0) + COALESCE(activity.expense_total, 0)) DESC, balances.name",
                new String[]{String.valueOf(startInclusive), String.valueOf(endExclusive)});
    }

    public Map<String, Long> getMonthlyComparison(long currentStart, long currentEnd, long previousStart, long previousEnd) {
        HashMap<String, Long> comparison = new HashMap<>();
        Map<String, Long> current = getIncomeExpenseSummary(currentStart, currentEnd);
        Map<String, Long> previous = getIncomeExpenseSummary(previousStart, previousEnd);
        long currentIncome = current.get("income");
        long currentExpense = current.get("expense");
        long previousIncome = previous.get("income");
        long previousExpense = previous.get("expense");

        comparison.put("current_income", currentIncome);
        comparison.put("current_expense", currentExpense);
        comparison.put("current_net", currentIncome - currentExpense);
        comparison.put("previous_income", previousIncome);
        comparison.put("previous_expense", previousExpense);
        comparison.put("previous_net", previousIncome - previousExpense);
        return comparison;
    }

    public Cursor getBorrowLendRecords() {
        return getReadableDatabase().rawQuery(
                "SELECT bl.id, bl.name, bl.amount, bl.type, bl.is_completed, bl.created_at, bl.completed_at, a.name AS account " +
                        "FROM borrow_lend bl " +
                        "JOIN bank_accounts a ON a.id = bl.bank_account_id " +
                        "ORDER BY bl.is_completed ASC, bl.created_at DESC", null);
    }

    public Cursor getNonReturnedSpendingRecords() {
        return getReadableDatabase().rawQuery(
                "SELECT nrs.id, nrs.name, nrs.amount, nrs.created_at, nrs.transaction_id, " +
                        "COALESCE(a.name, '') AS account " +
                        "FROM non_returned_spending nrs " +
                        "LEFT JOIN bank_accounts a ON a.id = nrs.bank_account_id " +
                        "ORDER BY nrs.created_at DESC", null);
    }

    public long getTotalBalance() {
        long total = 0;
        Cursor cursor = getAccountsWithBalances();
        try {
            while (cursor.moveToNext()) {
                total += cursor.getLong(cursor.getColumnIndexOrThrow("balance"));
            }
        } finally {
            cursor.close();
        }
        return total;
    }

    public long getTotalForType(String type, long startInclusive, long endExclusive) {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(amount), 0) FROM transactions " +
                        "WHERE type = ? AND transaction_date >= ? AND transaction_date < ?",
                new String[]{type, String.valueOf(startInclusive), String.valueOf(endExclusive)});
        try {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0;
        } finally {
            cursor.close();
        }
    }

    public int getTransactionCount(long startInclusive, long endExclusive) {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM transactions WHERE transaction_date >= ? AND transaction_date < ?",
                new String[]{String.valueOf(startInclusive), String.valueOf(endExclusive)});
        try {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        } finally {
            cursor.close();
        }
    }

    public Cursor getCategoryTotals(String type, long startInclusive, long endExclusive, int limit) {
        return getReadableDatabase().rawQuery(
                "SELECT c.name, COALESCE(SUM(t.amount), 0) AS total FROM transactions t " +
                        "JOIN categories c ON c.id = t.category_id " +
                        "WHERE t.type = ? AND t.transaction_date >= ? AND t.transaction_date < ? " +
                        "GROUP BY c.id, c.name ORDER BY total DESC LIMIT ?",
                new String[]{type, String.valueOf(startInclusive), String.valueOf(endExclusive), String.valueOf(limit)});
    }

    public Cursor getAccountActivityTotals(long startInclusive, long endExclusive, int limit) {
        return getReadableDatabase().rawQuery(
                "SELECT a.name, " +
                        "COALESCE(SUM(CASE WHEN t.type = 'income' THEN t.amount ELSE 0 END), 0) AS income_total, " +
                        "COALESCE(SUM(CASE WHEN t.type = 'expense' THEN t.amount ELSE 0 END), 0) AS expense_total " +
                        "FROM bank_accounts a " +
                        "LEFT JOIN transactions t ON t.bank_account_id = a.id " +
                        "AND t.transaction_date >= ? AND t.transaction_date < ? " +
                        "GROUP BY a.id, a.name " +
                        "HAVING income_total > 0 OR expense_total > 0 " +
                        "ORDER BY (income_total + expense_total) DESC LIMIT ?",
                new String[]{String.valueOf(startInclusive), String.valueOf(endExclusive), String.valueOf(limit)});
    }

    public String getTopExpenseCategory(long startInclusive, long endExclusive) {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT c.name, SUM(t.amount) AS total FROM transactions t " +
                        "JOIN categories c ON c.id = t.category_id " +
                        "WHERE t.type = 'expense' AND t.transaction_date >= ? AND t.transaction_date < ? " +
                        "GROUP BY c.id, c.name ORDER BY total DESC LIMIT 1",
                new String[]{String.valueOf(startInclusive), String.valueOf(endExclusive)});
        try {
            if (cursor.moveToFirst()) {
                return cursor.getString(0) + " (" + formatMoney(cursor.getLong(1)) + ")";
            }
            return "No expenses yet";
        } finally {
            cursor.close();
        }
    }

    public boolean hasAccounts() {
        Cursor cursor = getReadableDatabase().rawQuery("SELECT id FROM bank_accounts LIMIT 1", null);
        try {
            return cursor.moveToFirst();
        } finally {
            cursor.close();
        }
    }

    public static long parseMoney(String input) {
        BigDecimal amount = new BigDecimal(input.trim());
        return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    public static String formatMoney(long cents) {
        NumberFormat format = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(0);
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(cents / 100.0);
    }

    public long[] getBorrowLendTotals() {
        long borrowed = 0;
        long lent = 0;
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT type, SUM(amount) FROM borrow_lend WHERE is_completed = 0 GROUP BY type", null);
        try {
            while (cursor.moveToNext()) {
                String type = cursor.getString(0);
                long sum = cursor.getLong(1);
                if (TYPE_BORROWED.equals(type)) {
                    borrowed = sum;
                } else if (TYPE_LENDED.equals(type)) {
                    lent = sum;
                }
            }
        } finally {
            cursor.close();
        }
        return new long[]{borrowed, lent};
    }


}

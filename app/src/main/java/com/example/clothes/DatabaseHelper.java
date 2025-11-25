package com.example.clothes;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "narek.db";
    private static final int DATABASE_VERSION = 2;
    private final Context context;
    private OnDataChangedListener onDataChangedListener;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context;

        if (!isDatabaseExists()) {
            copyDatabaseFromAssets();
        }

        debugDatabaseStructure();
        debugAssignmentsTable();
    }

    private boolean isDatabaseExists() {
        File dbFile = context.getDatabasePath(DATABASE_NAME);
        return dbFile.exists();
    }

    private void copyDatabaseFromAssets() {
        try {
            InputStream inputStream = context.getAssets().open("databases/" + DATABASE_NAME);
            File dbFile = context.getDatabasePath(DATABASE_NAME);
            File parentDir = dbFile.getParentFile();

            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            OutputStream outputStream = new FileOutputStream(dbFile);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            outputStream.flush();
            outputStream.close();
            inputStream.close();

            Log.d("DatabaseHelper", "✅ База данных скопирована из assets");
        } catch (IOException e) {
            Log.e("DatabaseHelper", "❌ Ошибка копирования БД: " + e.getMessage());
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Используем существующую БД из assets
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        try {
            Log.d("DatabaseHelper", "🔄 Обновление БД с версии " + oldVersion + " на " + newVersion);

            // Добавляем недостающие столбцы для контроля качества
            if (oldVersion < 2) {
                addMissingColumnsToAssignments(db);
            }

        } catch (Exception e) {
            Log.e("DatabaseHelper", "❌ Ошибка обновления БД: " + e.getMessage());
        }
    }

    private void addMissingColumnsToAssignments(SQLiteDatabase db) {
        try {
            // Проверяем существование столбцов
            Cursor cursor = db.rawQuery("PRAGMA table_info(assignments)", null);
            List<String> existingColumns = new ArrayList<>();
            while (cursor.moveToNext()) {
                existingColumns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")));
            }
            cursor.close();

            // Добавляем недостающие столбцы
            if (!existingColumns.contains("quality_checked")) {
                db.execSQL("ALTER TABLE assignments ADD COLUMN quality_checked INTEGER DEFAULT 0");
                Log.d("DatabaseHelper", "✅ Добавлен столбец quality_checked");
            }

            if (!existingColumns.contains("quality_checker_id")) {
                db.execSQL("ALTER TABLE assignments ADD COLUMN quality_checker_id INTEGER");
                Log.d("DatabaseHelper", "✅ Добавлен столбец quality_checker_id");
            }

            if (!existingColumns.contains("quality_check_date")) {
                db.execSQL("ALTER TABLE assignments ADD COLUMN quality_check_date TEXT");
                Log.d("DatabaseHelper", "✅ Добавлен столбец quality_check_date");
            }

            if (!existingColumns.contains("quality_notes")) {
                db.execSQL("ALTER TABLE assignments ADD COLUMN quality_notes TEXT");
                Log.d("DatabaseHelper", "✅ Добавлен столбец quality_notes");
            }

        } catch (Exception e) {
            Log.e("DatabaseHelper", "❌ Ошибка добавления столбцов: " + e.getMessage());
        }
    }

    // === МЕТОДЫ ДЛЯ АУТЕНТИФИКАЦИИ И РЕГИСТРАЦИИ ===

    public User authenticateUser(String email, String password) {
        SQLiteDatabase db = getReadableDatabase();
        User user = null;

        try {
            String query = "SELECT * FROM users WHERE email = ? AND password_hash = ?";
            Cursor cursor = db.rawQuery(query, new String[]{email, password});

            if (cursor.moveToFirst()) {
                user = new User(
                        cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                        cursor.getString(cursor.getColumnIndexOrThrow("name")),
                        cursor.getString(cursor.getColumnIndexOrThrow("email")),
                        cursor.getString(cursor.getColumnIndexOrThrow("role")),
                        cursor.getString(cursor.getColumnIndexOrThrow("brigade")),
                        cursor.getString(cursor.getColumnIndexOrThrow("position")),
                        cursor.getString(cursor.getColumnIndexOrThrow("avatar_url"))
                );
                Log.d("DatabaseHelper", "✅ Пользователь найден: " + user.getName());
            } else {
                Log.d("DatabaseHelper", "❌ Пользователь не найден: " + email);
            }
            cursor.close();
        } catch (Exception e) {
            Log.e("DatabaseHelper", "❌ Ошибка аутентификации: " + e.getMessage());
        }
        return user;
    }

    public boolean isEmailExists(String email) {
        SQLiteDatabase db = getReadableDatabase();

        try {
            String query = "SELECT COUNT(*) FROM users WHERE email = ?";
            Cursor cursor = db.rawQuery(query, new String[]{email});

            boolean exists = false;
            if (cursor.moveToFirst()) {
                exists = cursor.getInt(0) > 0;
            }

            cursor.close();
            return exists;

        } catch (Exception e) {
            Log.e("DatabaseHelper", "❌ Ошибка проверки email: " + e.getMessage());
            return false;
        }
    }

    public boolean updatePassword(String email, String newPassword) {
        SQLiteDatabase db = getWritableDatabase();

        try {
            ContentValues values = new ContentValues();
            values.put("password_hash", newPassword);
            values.put("updated_at", getCurrentDateTime());

            int rows = db.update("users", values, "email = ?", new String[]{email});
            return rows > 0;

        } catch (Exception e) {
            Log.e("DatabaseHelper", "❌ Ошибка обновления пароля: " + e.getMessage());
            return false;
        }
    }

    public User getUserByEmail(String email) {
        SQLiteDatabase db = getReadableDatabase();
        User user = null;

        try {
            String query = "SELECT * FROM users WHERE email = ?";
            Cursor cursor = db.rawQuery(query, new String[]{email});

            if (cursor.moveToFirst()) {
                user = new User(
                        cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                        cursor.getString(cursor.getColumnIndexOrThrow("name")),
                        cursor.getString(cursor.getColumnIndexOrThrow("email")),
                        cursor.getString(cursor.getColumnIndexOrThrow("role")),
                        cursor.getString(cursor.getColumnIndexOrThrow("brigade")),
                        cursor.getString(cursor.getColumnIndexOrThrow("position")),
                        cursor.getString(cursor.getColumnIndexOrThrow("avatar_url"))
                );
            }

            cursor.close();
        } catch (Exception e) {
            Log.e("DatabaseHelper", "❌ Ошибка получения пользователя: " + e.getMessage());
        }

        return user;
    }

    public boolean registerUser(String email, String password, String name, String brigade, String position) {
        SQLiteDatabase db = getWritableDatabase();

        try {
            if (isEmailExists(email)) {
                Log.e("DatabaseHelper", "❌ Пользователь с email " + email + " уже существует");
                return false;
            }

            ContentValues values = new ContentValues();
            values.put("email", email);
            values.put("password_hash", password);
            values.put("name", name);
            values.put("brigade", brigade);
            values.put("position", position);
            values.put("role", "worker");
            values.put("created_at", getCurrentDateTime());
            values.put("updated_at", getCurrentDateTime());

            long result = db.insert("users", null, values);
            return result != -1;

        } catch (Exception e) {
            Log.e("DatabaseHelper", "❌ Ошибка регистрации: " + e.getMessage());
            return false;
        }
    }

    // === ДАННЫЕ ДЛЯ WORKER ===

    public WorkerStats getWorkerStats(int userId) {
        SQLiteDatabase db = getReadableDatabase();
        WorkerStats stats = new WorkerStats();

        try {
            String query = "SELECT " +
                    "COALESCE(SUM(a.actual_quantity), 0) as completed, " +
                    "COALESCE(SUM(a.defects), 0) as defects " +
                    "FROM assignments a " +
                    "WHERE a.user_id = ?";

            Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(userId)});
            if (cursor.moveToFirst()) {
                stats.completed = cursor.getInt(0);
                stats.defects = cursor.getInt(1);
            }
            cursor.close();

            stats.todayAssignments = getTodayAssignments(userId);

            Log.d("DatabaseHelper", "📊 Статистика worker " + userId + ": completed=" + stats.completed + ", defects=" + stats.defects);

        } catch (Exception e) {
            Log.e("DatabaseHelper", "❌ Ошибка получения статистики worker: " + e.getMessage());
        }
        return stats;
    }

    private String getTodayAssignments(int userId) {
        SQLiteDatabase db = getReadableDatabase();
        StringBuilder assignments = new StringBuilder();

        try {
            String query = "SELECT o.name as operation_name, " +
                    "a.planned_quantity, a.actual_quantity, a.status " +
                    "FROM assignments a " +
                    "JOIN operations o ON a.operation_id = o.id " +
                    "WHERE a.user_id = ? AND a.status IN ('assigned', 'in_progress') " +
                    "ORDER BY a.status DESC, a.id LIMIT 3";

            Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(userId)});
            int count = 0;

            while (cursor.moveToNext() && count < 3) {
                String opName = cursor.getString(cursor.getColumnIndexOrThrow("operation_name"));
                int planned = cursor.getInt(cursor.getColumnIndexOrThrow("planned_quantity"));
                int actual = cursor.getInt(cursor.getColumnIndexOrThrow("actual_quantity"));
                String status = cursor.getString(cursor.getColumnIndexOrThrow("status"));

                if (opName == null) opName = "Задание " + (count + 1);

                assignments.append(opName).append("    ")
                        .append(actual).append("/").append(planned).append(" шт")
                        .append(" (").append(getStatusText(status)).append(")\n");
                count++;
            }
            cursor.close();

        } catch (Exception e) {
            Log.e("DatabaseHelper", "❌ Ошибка получения заданий: " + e.getMessage());
        }

        if (assignments.length() == 0) {
            assignments.append("Нет активных заданий\n");
        }

        return assignments.toString();
    }

    // === МЕТОДЫ ДЛЯ ФУНКЦИОНАЛА КНОПОК WORKER ===

    public WorkerDetailedStats getWorkerDetailedStats(int userId) {
        SQLiteDatabase db = getReadableDatabase();
        WorkerDetailedStats stats = new WorkerDetailedStats();

        try {
            WorkerStats basicStats = getWorkerStats(userId);
            stats.todayCompleted = basicStats.completed;
            stats.todayDefects = basicStats.defects;
            stats.weekCompleted = basicStats.completed;
            stats.weekDefects = basicStats.defects;

            stats.activeAssignments = getActiveAssignments(userId);
            stats.recentCompleted = getRecentCompletedAssignments(userId);

            Log.d("DatabaseHelper", "📊 Детальная статистика worker " + userId + ": today=" + stats.todayCompleted + ", week=" + stats.weekCompleted);

        } catch (Exception e) {
            Log.e("DatabaseHelper", "❌ Ошибка получения детальной статистики: " + e.getMessage());
        }
        return stats;
    }

    private List<Assignment> getActiveAssignments(int userId) {
        SQLiteDatabase db = getReadableDatabase();
        List<Assignment> assignments = new ArrayList<>();

        try {
            String query = "SELECT a.id, o.name as operation_name, " +
                    "a.planned_quantity, a.actual_quantity, a.status, " +
                    "p.name as product_name, a.start_time " +
                    "FROM assignments a " +
                    "JOIN operations o ON a.operation_id = o.id " +
                    "LEFT JOIN products p ON o.product_id = p.id " +
                    "WHERE a.user_id = ? AND a.status IN ('assigned', 'in_progress') " +
                    "ORDER BY a.created_at DESC LIMIT 5";

            Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(userId)});
            while (cursor.moveToNext()) {
                Assignment assignment = new Assignment();
                assignment.id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                assignment.operationName = cursor.getString(cursor.getColumnIndexOrThrow("operation_name"));
                assignment.plannedQuantity = cursor.getInt(cursor.getColumnIndexOrThrow("planned_quantity"));
                assignment.actualQuantity = cursor.getInt(cursor.getColumnIndexOrThrow("actual_quantity"));
                assignment.status = cursor.getString(cursor.getColumnIndexOrThrow("status"));
                assignment.productName = cursor.getString(cursor.getColumnIndexOrThrow("product_name"));
                assignment.startTime = cursor.getString(cursor.getColumnIndexOrThrow("start_time"));
                assignments.add(assignment);
            }
            cursor.close();

        } catch (Exception e) {
            Log.e("DatabaseHelper", "❌ Ошибка получения активных заданий: " + e.getMessage());
        }
        return assignments;
    }

    private List<Assignment> getRecentCompletedAssignments(int userId) {
        SQLiteDatabase db = getReadableDatabase();
        List<Assignment> assignments = new ArrayList<>();

        try {
            String query = "SELECT a.id, o.name as operation_name, " +
                    "a.planned_quantity, a.actual_quantity, a.defects, " +
                    "p.name as product_name, a.end_time " +
                    "FROM assignments a " +
                    "JOIN operations o ON a.operation_id = o.id " +
                    "LEFT JOIN products p ON o.product_id = p.id " +
                    "WHERE a.user_id = ? AND a.status = 'completed' " +
                    "ORDER BY a.end_time DESC LIMIT 5";

            Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(userId)});
            while (cursor.moveToNext()) {
                Assignment assignment = new Assignment();
                assignment.id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                assignment.operationName = cursor.getString(cursor.getColumnIndexOrThrow("operation_name"));
                assignment.plannedQuantity = cursor.getInt(cursor.getColumnIndexOrThrow("planned_quantity"));
                assignment.actualQuantity = cursor.getInt(cursor.getColumnIndexOrThrow("actual_quantity"));
                assignment.defects = cursor.getInt(cursor.getColumnIndexOrThrow("defects"));
                assignment.productName = cursor.getString(cursor.getColumnIndexOrThrow("product_name"));
                assignment.endTime = cursor.getString(cursor.getColumnIndexOrThrow("end_time"));
                assignments.add(assignment);
            }
            cursor.close();

        } catch (Exception e) {
            Log.e("DatabaseHelper", "❌ Ошибка получения выполненных заданий: " + e.getMessage());
        }
        return assignments;
    }

    public List<Assignment> getAvailableAssignments(int userId) {
        SQLiteDatabase db = getReadableDatabase();
        List<Assignment> assignments = new ArrayList<>();

        try {
            String query = "SELECT a.id, o.name as operation_name, " +
                    "a.planned_quantity, a.actual_quantity, a.status, " +
                    "p.name as product_name, o.standard_time_minutes " +
                    "FROM assignments a " +
                    "JOIN operations o ON a.operation_id = o.id " +
                    "LEFT JOIN products p ON o.product_id = p.id " +
                    "WHERE a.user_id = ? AND a.status IN ('assigned', 'in_progress') " +
                    "ORDER BY a.created_at";

            Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(userId)});
            while (cursor.moveToNext()) {
                Assignment assignment = new Assignment();
                assignment.id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                assignment.operationName = cursor.getString(cursor.getColumnIndexOrThrow("operation_name"));
                assignment.plannedQuantity = cursor.getInt(cursor.getColumnIndexOrThrow("planned_quantity"));
                assignment.actualQuantity = cursor.getInt(cursor.getColumnIndexOrThrow("actual_quantity"));
                assignment.status = cursor.getString(cursor.getColumnIndexOrThrow("status"));
                assignment.productName = cursor.getString(cursor.getColumnIndexOrThrow("product_name"));
                assignment.standardTime = cursor.getInt(cursor.getColumnIndexOrThrow("standard_time_minutes"));
                assignments.add(assignment);
            }
            cursor.close();

            Log.d("DatabaseHelper", "✅ Найдено доступных заданий для user " + userId + ": " + assignments.size());

        } catch (Exception e) {
            Log.e("DatabaseHelper", "❌ Ошибка получения доступных заданий: " + e.getMessage());
        }
        return assignments;
    }

    public Assignment getAssignmentById(int assignmentId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Assignment assignment = null;

        try {
            String query = "SELECT a.id, o.name as operation_name, " +
                    "a.planned_quantity, a.actual_quantity, a.defects, a.status, " +
                    "p.name as product_name, a.start_time, a.end_time " +
                    "FROM assignments a " +
                    "JOIN operations o ON a.operation_id = o.id " +
                    "LEFT JOIN products p ON o.product_id = p.id " +
                    "WHERE a.id = ?";

            Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(assignmentId)});
            if (cursor != null && cursor.moveToFirst()) {
                assignment = new Assignment();
                assignment.id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                assignment.operationName = cursor.getString(cursor.getColumnIndexOrThrow("operation_name"));
                assignment.plannedQuantity = cursor.getInt(cursor.getColumnIndexOrThrow("planned_quantity"));
                assignment.actualQuantity = cursor.getInt(cursor.getColumnIndexOrThrow("actual_quantity"));
                assignment.defects = cursor.getInt(cursor.getColumnIndexOrThrow("defects"));
                assignment.status = cursor.getString(cursor.getColumnIndexOrThrow("status"));
                assignment.productName = cursor.getString(cursor.getColumnIndexOrThrow("product_name"));
                assignment.startTime = cursor.getString(cursor.getColumnIndexOrThrow("start_time"));
                assignment.endTime = cursor.getString(cursor.getColumnIndexOrThrow("end_time"));
            }

            if (cursor != null) {
                cursor.close();
            }
        } catch (Exception e) {
            Log.e("DatabaseHelper", "❌ Ошибка получения задания по ID: " + e.getMessage());
        }

        return assignment;
    }

    public boolean updateAssignmentStatus(int assignmentId, String newStatus) {
        SQLiteDatabase db = this.getWritableDatabase();

        try {
            // Сначала проверим существование задания
            Cursor cursor = db.query("assignments",
                    new String[]{"id", "planned_quantity", "actual_quantity"},
                    "id = ?", new String[]{String.valueOf(assignmentId)},
                    null, null, null);

            if (cursor == null || !cursor.moveToFirst()) {
                Log.e("DatabaseHelper", "❌ Задание не найдено: " + assignmentId);
                if (cursor != null) cursor.close();
                return false;
            }
            cursor.close();

            ContentValues values = new ContentValues();
            values.put("status", newStatus);

            if ("in_progress".equals(newStatus)) {
                values.put("start_time", getCurrentDateTime());
            } else if ("completed".equals(newStatus)) {
                values.put("end_time", getCurrentDateTime());
                Log.d("DatabaseHelper", "✅ Задание " + assignmentId + " помечено как выполненное");
            }

            int rowsAffected = db.update("assignments", values, "id = ?", new String[]{String.valueOf(assignmentId)});

            // Уведомляем об изменении статуса
            if (rowsAffected > 0 && onDataChangedListener != null) {
                onDataChangedListener.onAssignmentStatusChanged(assignmentId, newStatus);

                // Дополнительно уведомляем о возможности проверки качества для завершенных заданий
                if ("completed".equals(newStatus)) {
                    onDataChangedListener.onQualityCheckPerformed(assignmentId);
                    Log.d("DatabaseHelper", "🔔 Уведомление о новом задании для контроля качества: " + assignmentId);
                }
            }

            Log.d("DatabaseHelper", "🔄 Обновление статуса задания " + assignmentId + " на '" + newStatus + "': " +
                    (rowsAffected > 0 ? "успешно" : "ошибка") + ", затронуто строк: " + rowsAffected);
            return rowsAffected > 0;

        } catch (Exception e) {
            Log.e("DatabaseHelper", "❌ Ошибка обновления статуса: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            if (db != null) {
                db.close();
            }
        }
    }

    private boolean isValidStatus(String status) {
        return "assigned".equals(status) ||
                "in_progress".equals(status) ||
                "completed".equals(status) ||
                "cancelled".equals(status);
    }

    public boolean recordOperationCompletion(int assignmentId, int quantity, int defects) {
        SQLiteDatabase db = this.getWritableDatabase();

        try {
            Assignment assignment = null;
            Cursor cursor = db.query("assignments",
                    new String[]{"id", "planned_quantity", "actual_quantity", "defects", "status"},
                    "id = ?", new String[]{String.valueOf(assignmentId)},
                    null, null, null);

            if (cursor != null && cursor.moveToFirst()) {
                assignment = new Assignment();
                assignment.id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                assignment.plannedQuantity = cursor.getInt(cursor.getColumnIndexOrThrow("planned_quantity"));
                assignment.actualQuantity = cursor.getInt(cursor.getColumnIndexOrThrow("actual_quantity"));
                assignment.defects = cursor.getInt(cursor.getColumnIndexOrThrow("defects"));
                assignment.status = cursor.getString(cursor.getColumnIndexOrThrow("status"));
                cursor.close();
            }

            if (assignment == null) {
                Log.e("DatabaseHelper", "❌ Задание не найдено: " + assignmentId);
                return false;
            }

            int newActualQuantity = assignment.actualQuantity + quantity;
            int newDefects = assignment.defects + defects;

            ContentValues values = new ContentValues();
            values.put("actual_quantity", newActualQuantity);
            values.put("defects", newDefects);

            String newStatus = assignment.status;
            if (newActualQuantity >= assignment.plannedQuantity) {
                newStatus = "completed";
                values.put("status", "completed");
                values.put("end_time", getCurrentDateTime());
                Log.d("DatabaseHelper", "✅ Задание " + assignmentId + " завершено");
            } else if ("assigned".equals(assignment.status)) {
                newStatus = "in_progress";
                values.put("status", "in_progress");
                values.put("start_time", getCurrentDateTime());
                Log.d("DatabaseHelper", "🔄 Задание " + assignmentId + " взято в работу");
            }

            int rowsAffected = db.update("assignments", values, "id = ?", new String[]{String.valueOf(assignmentId)});

            // Уведомляем об изменении данных
            if (rowsAffected > 0 && onDataChangedListener != null) {
                onDataChangedListener.onAssignmentStatusChanged(assignmentId, newStatus);

                // Если задание завершено, дополнительно уведомляем о возможности проверки качества
                if ("completed".equals(newStatus)) {
                    onDataChangedListener.onQualityCheckPerformed(assignmentId);
                }
            }

            Log.d("DatabaseHelper", "📝 Учет выполнения задания " + assignmentId + ": +" + quantity + " шт, брак: " + defects +
                    " -> " + (rowsAffected > 0 ? "успешно" : "ошибка") + ", новый статус: " + newStatus);

            return rowsAffected > 0;

        } catch (Exception e) {
            Log.e("DatabaseHelper", "❌ Ошибка учета выполнения: " + e.getMessage());
            return false;
        } finally {
            db.close();
        }
    }

    // === МЕТОДЫ ДЛЯ КОНТРОЛЯ КАЧЕСТВА ===

    public List<QualityControlItem> getQualityControlTasks() {
        SQLiteDatabase db = getReadableDatabase();
        List<QualityControlItem> tasks = new ArrayList<>();

        try {
            // Используем таблицу quality_checks для контроля качества
            String query = "SELECT a.id, u.name as worker_name, o.name as operation_name, " +
                    "p.name as product_name, a.planned_quantity, a.actual_quantity, " +
                    "a.defects, a.status, a.created_at, a.start_time, a.end_time, " +
                    "qc.result as quality_result, qc.defects_found, qc.comments as quality_notes, " +
                    "qc.check_date as quality_check_date, qc.inspector_id as quality_checker_id, " +
                    "CASE WHEN qc.id IS NOT NULL THEN 1 ELSE 0 END as quality_checked " +
                    "FROM assignments a " +
                    "JOIN users u ON a.user_id = u.id " +
                    "JOIN operations o ON a.operation_id = o.id " +
                    "LEFT JOIN products p ON o.product_id = p.id " +
                    "LEFT JOIN quality_checks qc ON a.id = qc.assignment_id " +
                    "WHERE a.status = 'completed' " +
                    "ORDER BY qc.check_date DESC, a.end_time DESC";

            Cursor cursor = db.rawQuery(query, null);
            while (cursor.moveToNext()) {
                QualityControlItem task = new QualityControlItem();
                task.id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                task.workerName = cursor.getString(cursor.getColumnIndexOrThrow("worker_name"));
                task.operationName = cursor.getString(cursor.getColumnIndexOrThrow("operation_name"));
                task.productName = cursor.getString(cursor.getColumnIndexOrThrow("product_name"));
                task.plannedQuantity = cursor.getInt(cursor.getColumnIndexOrThrow("planned_quantity"));
                task.actualQuantity = cursor.getInt(cursor.getColumnIndexOrThrow("actual_quantity"));
                task.defects = cursor.getInt(cursor.getColumnIndexOrThrow("defects"));
                task.status = cursor.getString(cursor.getColumnIndexOrThrow("status"));
                task.createdAt = cursor.getString(cursor.getColumnIndexOrThrow("created_at"));
                task.startTime = cursor.getString(cursor.getColumnIndexOrThrow("start_time"));
                task.endTime = cursor.getString(cursor.getColumnIndexOrThrow("end_time"));

                // Используем данные из quality_checks
                String qualityResult = cursor.getString(cursor.getColumnIndexOrThrow("quality_result"));
                task.qualityChecked = qualityResult != null;
                task.qualityCheckerId = cursor.getInt(cursor.getColumnIndexOrThrow("quality_checker_id"));
                task.qualityCheckDate = cursor.getString(cursor.getColumnIndexOrThrow("quality_check_date"));
                task.qualityNotes = cursor.getString(cursor.getColumnIndexOrThrow("quality_notes"));

                tasks.add(task);
            }
            cursor.close();

            Log.d("DatabaseHelper", "✅ Найдено заданий для контроля качества: " + tasks.size());

        } catch (Exception e) {
            Log.e("DatabaseHelper", "❌ Ошибка получения заданий для контроля качества: " + e.getMessage());
        }
        return tasks;
    }
    public List<QualityControlItem> getWorkerQualityControlTasks(int workerId) {
        SQLiteDatabase db = getReadableDatabase();
        List<QualityControlItem> tasks = new ArrayList<>();

        try {
            String query = "SELECT a.id, u.name as worker_name, o.name as operation_name, " +
                    "p.name as product_name, a.planned_quantity, a.actual_quantity, " +
                    "a.defects, a.status, a.created_at, a.start_time, a.end_time, " +
                    "qc.result as quality_result, qc.defects_found, qc.comments as quality_notes, " +
                    "qc.check_date as quality_check_date, qc.inspector_id as quality_checker_id, " +
                    "CASE WHEN qc.id IS NOT NULL THEN 1 ELSE 0 END as quality_checked " +
                    "FROM assignments a " +
                    "JOIN users u ON a.user_id = u.id " +
                    "JOIN operations o ON a.operation_id = o.id " +
                    "LEFT JOIN products p ON o.product_id = p.id " +
                    "LEFT JOIN quality_checks qc ON a.id = qc.assignment_id " +
                    "WHERE a.user_id = ? AND a.status = 'completed' " +
                    "ORDER BY qc.check_date DESC, a.end_time DESC";

            Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(workerId)});
            while (cursor.moveToNext()) {
                QualityControlItem task = new QualityControlItem();
                task.id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                task.workerName = cursor.getString(cursor.getColumnIndexOrThrow("worker_name"));
                task.operationName = cursor.getString(cursor.getColumnIndexOrThrow("operation_name"));
                task.productName = cursor.getString(cursor.getColumnIndexOrThrow("product_name"));
                task.plannedQuantity = cursor.getInt(cursor.getColumnIndexOrThrow("planned_quantity"));
                task.actualQuantity = cursor.getInt(cursor.getColumnIndexOrThrow("actual_quantity"));
                task.defects = cursor.getInt(cursor.getColumnIndexOrThrow("defects"));
                task.status = cursor.getString(cursor.getColumnIndexOrThrow("status"));
                task.createdAt = cursor.getString(cursor.getColumnIndexOrThrow("created_at"));
                task.startTime = cursor.getString(cursor.getColumnIndexOrThrow("start_time"));
                task.endTime = cursor.getString(cursor.getColumnIndexOrThrow("end_time"));

                // Используем данные из quality_checks
                String qualityResult = cursor.getString(cursor.getColumnIndexOrThrow("quality_result"));
                task.qualityChecked = qualityResult != null;
                task.qualityCheckerId = cursor.getInt(cursor.getColumnIndexOrThrow("quality_checker_id"));
                task.qualityCheckDate = cursor.getString(cursor.getColumnIndexOrThrow("quality_check_date"));
                task.qualityNotes = cursor.getString(cursor.getColumnIndexOrThrow("quality_notes"));

                tasks.add(task);
            }
            cursor.close();

            Log.d("DatabaseHelper", "✅ Найдено заданий работника " + workerId + " для контроля качества: " + tasks.size());

        } catch (Exception e) {
            Log.e("DatabaseHelper", "❌ Ошибка получения заданий работника: " + e.getMessage());
        }
        return tasks;
    }
    public boolean checkAssignmentQuality(int assignmentId, int checkerId, int approvedQuantity, int defectsFound, String notes) {
        SQLiteDatabase db = getWritableDatabase();

        try {
            // Обновляем основную таблицу assignments
            ContentValues assignmentValues = new ContentValues();
            assignmentValues.put("actual_quantity", approvedQuantity);
            assignmentValues.put("defects", defectsFound);

            int rowsAffected = db.update("assignments", assignmentValues, "id = ?", new String[]{String.valueOf(assignmentId)});

            // Добавляем запись в таблицу quality_checks
            ContentValues qualityValues = new ContentValues();
            qualityValues.put("assignment_id", assignmentId);
            qualityValues.put("inspector_id", checkerId);
            qualityValues.put("result", approvedQuantity > 0 ? "approved" : "rejected");
            qualityValues.put("defects_found", defectsFound);
            qualityValues.put("comments", notes);
            qualityValues.put("check_date", getCurrentDateTime());

            long qualityResult = db.insert("quality_checks", null, qualityValues);

            boolean success = rowsAffected > 0 && qualityResult != -1;
            if (success) {
                if (onDataChangedListener != null) {
                    onDataChangedListener.onQualityCheckPerformed(assignmentId);
                }
                Log.d("DatabaseHelper", "✅ Контроль качества выполнен для задания " + assignmentId);
            }

            return success;

        } catch (Exception e) {
            Log.e("DatabaseHelper", "❌ Ошибка проверки качества: " + e.getMessage());
            return false;
        }
    }
    // === МЕТОД ДЛЯ МАССОВОЙ ПРОВЕРКИ КАЧЕСТВА ===
    public boolean bulkCheckWorkerQuality(int workerId, int checkerId, String notes) {
        SQLiteDatabase db = getWritableDatabase();

        try {
            // Находим все завершенные задания работника без проверки качества
            Cursor cursor = db.rawQuery(
                    "SELECT a.id, a.actual_quantity, a.defects " +
                            "FROM assignments a " +
                            "LEFT JOIN quality_checks qc ON a.id = qc.assignment_id " +
                            "WHERE a.user_id = ? AND a.status = 'completed' AND qc.id IS NULL",
                    new String[]{String.valueOf(workerId)}
            );

            int successCount = 0;
            while (cursor.moveToNext()) {
                int assignmentId = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                int actualQuantity = cursor.getInt(cursor.getColumnIndexOrThrow("actual_quantity"));
                int defects = cursor.getInt(cursor.getColumnIndexOrThrow("defects"));

                ContentValues values = new ContentValues();
                values.put("assignment_id", assignmentId);
                values.put("inspector_id", checkerId);
                values.put("result", "approved");
                values.put("defects_found", defects);
                values.put("comments", notes);
                values.put("check_date", getCurrentDateTime());

                long result = db.insert("quality_checks", null, values);
                if (result != -1) {
                    successCount++;
                }
            }
            cursor.close();

            boolean success = successCount > 0;
            if (success && onDataChangedListener != null) {
                onDataChangedListener.onQualityCheckPerformed(-1);
            }

            Log.d("DatabaseHelper", "✅ Массовая проверка: " + successCount + " заданий работника " + workerId);

            return success;

        } catch (Exception e) {
            Log.e("DatabaseHelper", "❌ Ошибка массовой проверки: " + e.getMessage());
            return false;
        }
    }


    public QualityStats getQualityStats() {
        SQLiteDatabase db = getReadableDatabase();
        QualityStats stats = new QualityStats();

        try {
            String query = "SELECT " +
                    "COUNT(*) as total_assignments, " +
                    "SUM(CASE WHEN qc.id IS NOT NULL THEN 1 ELSE 0 END) as checked_assignments, " +
                    "SUM(a.actual_quantity) as total_completed, " +
                    "SUM(a.defects) as total_defects, " +
                    "COUNT(DISTINCT a.user_id) as total_workers " +
                    "FROM assignments a " +
                    "LEFT JOIN quality_checks qc ON a.id = qc.assignment_id " +
                    "WHERE a.status = 'completed'";

            Cursor cursor = db.rawQuery(query, null);
            if (cursor.moveToFirst()) {
                stats.totalAssignments = cursor.getInt(0);
                stats.checkedAssignments = cursor.getInt(1);
                stats.totalCompleted = cursor.getInt(2);
                stats.totalDefects = cursor.getInt(3);
                stats.totalWorkers = cursor.getInt(4);
            }
            cursor.close();

            stats.workerStats = getWorkersQualityStats();

            Log.d("DatabaseHelper", "📊 Статистика качества: " + stats.checkedAssignments +
                    "/" + stats.totalAssignments + " проверено");

        } catch (Exception e) {
            Log.e("DatabaseHelper", "❌ Ошибка получения статистики качества: " + e.getMessage());
        }
        return stats;
    }

    private List<WorkerQualityStats> getWorkersQualityStats() {
        SQLiteDatabase db = getReadableDatabase();
        List<WorkerQualityStats> workerStats = new ArrayList<>();

        try {
            String query = "SELECT u.id, u.name, u.position, " +
                    "COUNT(a.id) as total_assignments, " +
                    "SUM(CASE WHEN qc.id IS NOT NULL THEN 1 ELSE 0 END) as checked_assignments, " +
                    "SUM(a.actual_quantity) as total_completed, " +
                    "SUM(a.defects) as total_defects " +
                    "FROM users u " +
                    "LEFT JOIN assignments a ON u.id = a.user_id " +
                    "LEFT JOIN quality_checks qc ON a.id = qc.assignment_id " +
                    "WHERE u.role = 'worker' AND a.status = 'completed' " +
                    "GROUP BY u.id, u.name, u.position " +
                    "ORDER BY total_completed DESC";

            Cursor cursor = db.rawQuery(query, null);
            while (cursor.moveToNext()) {
                WorkerQualityStats stats = new WorkerQualityStats();
                stats.workerId = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                stats.workerName = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                stats.position = cursor.getString(cursor.getColumnIndexOrThrow("position"));
                stats.totalAssignments = cursor.getInt(cursor.getColumnIndexOrThrow("total_assignments"));
                stats.checkedAssignments = cursor.getInt(cursor.getColumnIndexOrThrow("checked_assignments"));
                stats.totalCompleted = cursor.getInt(cursor.getColumnIndexOrThrow("total_completed"));
                stats.totalDefects = cursor.getInt(cursor.getColumnIndexOrThrow("total_defects"));

                workerStats.add(stats);
            }
            cursor.close();

        } catch (Exception e) {
            Log.e("DatabaseHelper", "❌ Ошибка получения статистики работников: " + e.getMessage());
        }
        return workerStats;
    }

    // === ИНТЕРФЕЙС ДЛЯ ОБНОВЛЕНИЯ UI ===
    public interface OnDataChangedListener {
        void onWorkerStatsUpdated(int userId, WorkerStats stats);
        void onAssignmentsUpdated(int userId, List<Assignment> assignments);
        void onQualityCheckPerformed(int assignmentId);
        void onAssignmentStatusChanged(int assignmentId, String newStatus);
    }

    public void setOnDataChangedListener(OnDataChangedListener listener) {
        this.onDataChangedListener = listener;
    }

    // === МЕТОДЫ ДЛЯ MASTER DASHBOARD ===

    public List<Assignment> getBrigadeActiveAssignments(String brigade) {
        SQLiteDatabase db = getReadableDatabase();
        List<Assignment> assignments = new ArrayList<>();

        try {
            String query = "SELECT a.id, o.name as operation_name, u.name as worker_name, " +
                    "a.planned_quantity, a.actual_quantity, a.defects, a.status, " +
                    "p.name as product_name, a.start_time " +
                    "FROM assignments a " +
                    "JOIN users u ON a.user_id = u.id " +
                    "JOIN operations o ON a.operation_id = o.id " +
                    "LEFT JOIN products p ON o.product_id = p.id " +
                    "JOIN orders ord ON a.order_id = ord.id " +
                    "WHERE u.brigade = ? AND a.status IN ('assigned', 'in_progress') " +
                    "ORDER BY a.created_at DESC LIMIT 10";

            Cursor cursor = db.rawQuery(query, new String[]{brigade});
            while (cursor.moveToNext()) {
                Assignment assignment = new Assignment();
                assignment.id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                assignment.operationName = cursor.getString(cursor.getColumnIndexOrThrow("operation_name"));
                assignment.productName = cursor.getString(cursor.getColumnIndexOrThrow("worker_name"));
                assignment.plannedQuantity = cursor.getInt(cursor.getColumnIndexOrThrow("planned_quantity"));
                assignment.actualQuantity = cursor.getInt(cursor.getColumnIndexOrThrow("actual_quantity"));
                assignment.defects = cursor.getInt(cursor.getColumnIndexOrThrow("defects"));
                assignment.status = cursor.getString(cursor.getColumnIndexOrThrow("status"));
                assignment.startTime = cursor.getString(cursor.getColumnIndexOrThrow("start_time"));
                assignments.add(assignment);
            }
            cursor.close();

            Log.d("DatabaseHelper", "✅ Найдено активных заданий для бригады " + brigade + ": " + assignments.size());

        } catch (Exception e) {
            Log.e("DatabaseHelper", "❌ Ошибка получения активных заданий бригады: " + e.getMessage());
        }
        return assignments;
    }

    public List<Assignment> getBrigadeRecentCompleted(String brigade) {
        SQLiteDatabase db = getReadableDatabase();
        List<Assignment> assignments = new ArrayList<>();

        try {
            String query = "SELECT a.id, o.name as operation_name, u.name as worker_name, " +
                    "a.planned_quantity, a.actual_quantity, a.defects, a.status, " +
                    "p.name as product_name, a.end_time " +
                    "FROM assignments a " +
                    "JOIN users u ON a.user_id = u.id " +
                    "JOIN operations o ON a.operation_id = o.id " +
                    "LEFT JOIN products p ON o.product_id = p.id " +
                    "JOIN orders ord ON a.order_id = ord.id " +
                    "WHERE u.brigade = ? AND a.status = 'completed' " +
                    "ORDER BY a.end_time DESC LIMIT 10";

            Cursor cursor = db.rawQuery(query, new String[]{brigade});
            while (cursor.moveToNext()) {
                Assignment assignment = new Assignment();
                assignment.id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                assignment.operationName = cursor.getString(cursor.getColumnIndexOrThrow("operation_name"));
                assignment.productName = cursor.getString(cursor.getColumnIndexOrThrow("worker_name"));
                assignment.plannedQuantity = cursor.getInt(cursor.getColumnIndexOrThrow("planned_quantity"));
                assignment.actualQuantity = cursor.getInt(cursor.getColumnIndexOrThrow("actual_quantity"));
                assignment.defects = cursor.getInt(cursor.getColumnIndexOrThrow("defects"));
                assignment.status = cursor.getString(cursor.getColumnIndexOrThrow("status"));
                assignment.endTime = cursor.getString(cursor.getColumnIndexOrThrow("end_time"));
                assignments.add(assignment);
            }
            cursor.close();

            Log.d("DatabaseHelper", "✅ Найдено выполненных заданий для бригады " + brigade + ": " + assignments.size());

        } catch (Exception e) {
            Log.e("DatabaseHelper", "❌ Ошибка получения выполненных заданий бригады: " + e.getMessage());
        }
        return assignments;
    }

    public List<Assignment> getAvailableOperations() {
        SQLiteDatabase db = getReadableDatabase();
        List<Assignment> operations = new ArrayList<>();

        try {
            String query = "SELECT id, name as operation_name, standard_time_minutes as standard_time " +
                    "FROM operations ORDER BY sequence_order, name";

            Cursor cursor = db.rawQuery(query, null);
            while (cursor.moveToNext()) {
                Assignment operation = new Assignment();
                operation.id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                operation.operationName = cursor.getString(cursor.getColumnIndexOrThrow("operation_name"));
                operation.standardTime = cursor.getInt(cursor.getColumnIndexOrThrow("standard_time"));
                operations.add(operation);
            }
            cursor.close();

            Log.d("DatabaseHelper", "✅ Найдено доступных операций: " + operations.size());

        } catch (Exception e) {
            Log.e("DatabaseHelper", "❌ Ошибка получения операций: " + e.getMessage());
        }
        return operations;
    }

    public List<Worker> getBrigadeWorkers(String brigade) {
        SQLiteDatabase db = getReadableDatabase();
        List<Worker> workers = new ArrayList<>();

        try {
            String query = "SELECT u.id, u.name, u.position, " +
                    "COALESCE(SUM(a.actual_quantity), 0) as completed " +
                    "FROM users u " +
                    "LEFT JOIN assignments a ON u.id = a.user_id " +
                    "WHERE u.brigade = ? AND u.role = 'worker' " +
                    "GROUP BY u.id, u.name, u.position " +
                    "ORDER BY completed DESC LIMIT 5";

            Cursor cursor = db.rawQuery(query, new String[]{brigade});
            while (cursor.moveToNext()) {
                Worker worker = new Worker();
                worker.id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                worker.name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                worker.position = cursor.getString(cursor.getColumnIndexOrThrow("position"));
                worker.completed = cursor.getInt(cursor.getColumnIndexOrThrow("completed"));
                workers.add(worker);
            }
            cursor.close();

            Log.d("DatabaseHelper", "✅ Найдено работников бригады " + brigade + ": " + workers.size());

        } catch (Exception e) {
            Log.e("DatabaseHelper", "❌ Ошибка получения работников: " + e.getMessage());
        }
        return workers;
    }

    public StorekeeperStats getStorekeeperStats() {
        SQLiteDatabase db = getReadableDatabase();
        StorekeeperStats stats = new StorekeeperStats();

        try {
            String query = "SELECT name, unit, current_stock, min_stock " +
                    "FROM materials WHERE current_stock <= min_stock " +
                    "ORDER BY current_stock ASC LIMIT 5";

            Cursor cursor = db.rawQuery(query, null);
            while (cursor.moveToNext()) {
                Material material = new Material();
                material.name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                material.unit = cursor.getString(cursor.getColumnIndexOrThrow("unit"));
                material.currentStock = cursor.getDouble(cursor.getColumnIndexOrThrow("current_stock"));
                material.minStock = cursor.getDouble(cursor.getColumnIndexOrThrow("min_stock"));
                stats.lowStockMaterials.add(material);
            }
            cursor.close();

            stats.recentUsage = getRecentMaterialUsage();

            Log.d("DatabaseHelper", "✅ Статистика кладовщика: материалов с низким запасом - " + stats.lowStockMaterials.size());

        } catch (Exception e) {
            Log.e("DatabaseHelper", "❌ Ошибка получения статистики кладовщика: " + e.getMessage());
        }
        return stats;
    }

    public ManagerStats getManagerStats() {
        SQLiteDatabase db = getReadableDatabase();
        ManagerStats stats = new ManagerStats();

        try {
            String ordersQuery = "SELECT " +
                    "COUNT(*) as total_orders, " +
                    "SUM(CASE WHEN status = 'completed' THEN 1 ELSE 0 END) as completed_orders, " +
                    "SUM(CASE WHEN status = 'in_progress' THEN 1 ELSE 0 END) as in_progress_orders " +
                    "FROM orders";

            Cursor cursor = db.rawQuery(ordersQuery, null);
            if (cursor.moveToFirst()) {
                stats.totalOrders = cursor.getInt(0);
                stats.completedOrders = cursor.getInt(1);
                stats.inProgressOrders = cursor.getInt(2);
            }
            cursor.close();

            stats.brigadePerformance = getBrigadePerformance();

            Log.d("DatabaseHelper", "✅ Статистика менеджера: заказов - " + stats.totalOrders +
                    ", выполнено - " + stats.completedOrders);

        } catch (Exception e) {
            Log.e("DatabaseHelper", "❌ Ошибка получения статистики менеджера: " + e.getMessage());
        }
        return stats;
    }

    // Вспомогательные методы
    private String getRecentMaterialUsage() {
        SQLiteDatabase db = getReadableDatabase();
        StringBuilder usage = new StringBuilder();

        try {
            String query = "SELECT m.name, SUM(mu.quantity_used) as total_used, m.unit " +
                    "FROM material_usage mu " +
                    "JOIN materials m ON mu.material_id = m.id " +
                    "WHERE mu.usage_date >= date('now', '-7 days') " +
                    "GROUP BY m.name, m.unit " +
                    "ORDER BY total_used DESC LIMIT 3";

            Cursor cursor = db.rawQuery(query, null);
            usage.append("За неделю: ");
            boolean first = true;

            while (cursor.moveToNext()) {
                if (!first) {
                    usage.append(", ");
                }
                String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                double totalUsed = cursor.getDouble(cursor.getColumnIndexOrThrow("total_used"));
                String unit = cursor.getString(cursor.getColumnIndexOrThrow("unit"));

                usage.append(name).append(" - ").append(totalUsed).append(" ").append(unit);
                first = false;
            }
            cursor.close();

            if (first) {
                usage.append("нет данных об использовании");
            }

        } catch (Exception e) {
            Log.e("DatabaseHelper", "❌ Ошибка получения использования материалов: " + e.getMessage());
            usage.append("ошибка загрузки данных");
        }

        return usage.toString();
    }

    private String getBrigadePerformance() {
        SQLiteDatabase db = getReadableDatabase();
        StringBuilder performance = new StringBuilder();

        try {
            String query = "SELECT u.brigade, " +
                    "COALESCE(SUM(a.actual_quantity), 0) as completed, " +
                    "COALESCE(SUM(a.defects), 0) as defects " +
                    "FROM assignments a " +
                    "JOIN users u ON a.user_id = u.id " +
                    "WHERE u.brigade IS NOT NULL AND u.brigade != '' " +
                    "GROUP BY u.brigade";

            Cursor cursor = db.rawQuery(query, null);
            while (cursor.moveToNext()) {
                String brigade = cursor.getString(0);
                int completed = cursor.getInt(1);
                int defects = cursor.getInt(2);

                double quality = 100.0;
                if (completed > 0) {
                    quality = 100 - (defects * 100.0 / completed);
                }

                if (performance.length() > 0) {
                    performance.append(", ");
                }
                performance.append(brigade).append(": ").append(String.format("%.0f", quality)).append("%");
            }
            cursor.close();

        } catch (Exception e) {
            Log.e("DatabaseHelper", "❌ Ошибка получения производительности бригад: " + e.getMessage());
            return "ошибка загрузки данных";
        }

        return performance.length() > 0 ? performance.toString() : "нет данных";
    }

    // === ДАННЫЕ ДЛЯ MASTER ===
    public MasterStats getMasterStats(int userId, String brigade) {
        SQLiteDatabase db = getReadableDatabase();
        MasterStats stats = new MasterStats();

        try {
            if (brigade == null || brigade.isEmpty()) {
                Cursor userCursor = db.rawQuery("SELECT brigade FROM users WHERE id = ?",
                        new String[]{String.valueOf(userId)});
                if (userCursor.moveToFirst()) {
                    brigade = userCursor.getString(0);
                }
                userCursor.close();
            }

            if (brigade != null && !brigade.isEmpty()) {
                String query = "SELECT " +
                        "COUNT(DISTINCT a.user_id) as workers_count, " +
                        "COALESCE(SUM(a.actual_quantity), 0) as total_completed, " +
                        "COALESCE(SUM(a.defects), 0) as total_defects " +
                        "FROM assignments a " +
                        "JOIN users u ON a.user_id = u.id " +
                        "WHERE u.brigade = ?";

                Cursor cursor = db.rawQuery(query, new String[]{brigade});
                if (cursor.moveToFirst()) {
                    stats.workersCount = cursor.getInt(0);
                    stats.totalCompleted = cursor.getInt(1);
                    stats.totalDefects = cursor.getInt(2);
                }
                cursor.close();

                stats.workers = getBrigadeWorkers(brigade);
            }

            Log.d("DatabaseHelper", "📊 Статистика master " + userId + ": workers=" + stats.workersCount +
                    ", completed=" + stats.totalCompleted + ", defects=" + stats.totalDefects);

        } catch (Exception e) {
            Log.e("DatabaseHelper", "❌ Ошибка получения статистики master: " + e.getMessage());
        }
        return stats;
    }

    // === МЕТОДЫ ДЛЯ НАЗНАЧЕНИЯ ЗАДАНИЙ ===

    public boolean assignTaskToWorker(int workerId, int operationId, int orderId, int plannedQuantity) {
        SQLiteDatabase db = getWritableDatabase();

        try {
            ContentValues values = new ContentValues();
            values.put("user_id", workerId);
            values.put("operation_id", operationId);
            values.put("order_id", orderId);
            values.put("planned_quantity", plannedQuantity);
            values.put("status", "assigned");
            values.put("created_at", getCurrentDateTime());

            long result = db.insert("assignments", null, values);
            boolean success = result != -1;

            // Уведомляем о новом задании
            if (success && onDataChangedListener != null) {
                onDataChangedListener.onAssignmentsUpdated(workerId, getAvailableAssignments(workerId));
                onDataChangedListener.onWorkerStatsUpdated(workerId, getWorkerStats(workerId));
            }

            Log.d("DatabaseHelper", "✅ Назначение задания worker " + workerId +
                    ", operation " + operationId + ": " + (success ? "успешно" : "ошибка"));

            return success;

        } catch (Exception e) {
            Log.e("DatabaseHelper", "❌ Ошибка назначения задания: " + e.getMessage());
            return false;
        }
    }

    public List<Order> getActiveOrders() {
        SQLiteDatabase db = getReadableDatabase();
        List<Order> orders = new ArrayList<>();

        try {
            String query = "SELECT id, order_number, customer_name, product_id, quantity, status " +
                    "FROM orders WHERE status IN ('new', 'in_progress') " +
                    "ORDER BY priority DESC, deadline ASC";

            Cursor cursor = db.rawQuery(query, null);
            while (cursor.moveToNext()) {
                Order order = new Order();
                order.id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                order.orderNumber = cursor.getString(cursor.getColumnIndexOrThrow("order_number"));
                order.customerName = cursor.getString(cursor.getColumnIndexOrThrow("customer_name"));
                order.productId = cursor.getInt(cursor.getColumnIndexOrThrow("product_id"));
                order.quantity = cursor.getInt(cursor.getColumnIndexOrThrow("quantity"));
                order.status = cursor.getString(cursor.getColumnIndexOrThrow("status"));
                orders.add(order);
            }
            cursor.close();

            Log.d("DatabaseHelper", "✅ Найдено активных заказов: " + orders.size());

        } catch (Exception e) {
            Log.e("DatabaseHelper", "❌ Ошибка получения активных заказов: " + e.getMessage());
        }
        return orders;
    }

    public Product getProductById(int productId) {
        SQLiteDatabase db = getReadableDatabase();
        Product product = null;

        try {
            String query = "SELECT id, article, name FROM products WHERE id = ?";
            Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(productId)});

            if (cursor.moveToFirst()) {
                product = new Product();
                product.id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                product.article = cursor.getString(cursor.getColumnIndexOrThrow("article"));
                product.name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
            }
            cursor.close();

        } catch (Exception e) {
            Log.e("DatabaseHelper", "❌ Ошибка получения продукта: " + e.getMessage());
        }
        return product;
    }

    // Вспомогательный метод для получения текущей даты и времени
    private String getCurrentDateTime() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        Date date = new Date();
        return dateFormat.format(date);
    }

    private String getStatusText(String status) {
        if (status == null) return "Назначено";

        switch (status) {
            case "assigned": return "Назначено";
            case "in_progress": return "В работе";
            case "completed": return "Выполнено";
            case "cancelled": return "Отменено";
            default: return status;
        }
    }

    // Отладка структуры БД
    public void debugDatabaseStructure() {
        SQLiteDatabase db = getReadableDatabase();

        try {
            Log.d("DatabaseDebug", "=== СТРУКТУРА БАЗЫ ДАННЫХ ===");

            // Покажем всех пользователей
            Log.d("DatabaseDebug", "👥 ПОЛЬЗОВАТЕЛИ:");
            Cursor cursor = db.rawQuery("SELECT id, name, email, role, brigade, position FROM users", null);
            while (cursor.moveToNext()) {
                int id = cursor.getInt(0);
                String name = cursor.getString(1);
                String email = cursor.getString(2);
                String role = cursor.getString(3);
                String brigade = cursor.getString(4);
                String position = cursor.getString(5);
                Log.d("DatabaseDebug", "   ID: " + id + ", " + name + " (" + email + "), " +
                        role + ", " + brigade + ", " + position);
            }
            cursor.close();

            // Покажем задания
            Log.d("DatabaseDebug", "📝 ЗАДАНИЯ:");
            cursor = db.rawQuery("SELECT a.id, u.name, o.name, a.planned_quantity, a.actual_quantity, a.defects, a.status " +
                    "FROM assignments a " +
                    "JOIN users u ON a.user_id = u.id " +
                    "JOIN operations o ON a.operation_id = o.id", null);
            while (cursor.moveToNext()) {
                int id = cursor.getInt(0);
                String userName = cursor.getString(1);
                String operationName = cursor.getString(2);
                int planned = cursor.getInt(3);
                int actual = cursor.getInt(4);
                int defects = cursor.getInt(5);
                String status = cursor.getString(6);
                Log.d("DatabaseDebug", "   ID: " + id + ", " + userName + " - " + operationName +
                        ", " + actual + "/" + planned + " шт, Брак: " + defects + ", Статус: " + status);
            }
            cursor.close();

        } catch (Exception e) {
            Log.e("DatabaseDebug", "Ошибка отладки БД: " + e.getMessage());
        }
    }

    // Отладка структуры таблицы assignments
    public void debugAssignmentsTable() {
        SQLiteDatabase db = getReadableDatabase();
        try {
            Log.d("DatabaseDebug", "=== СТРУКТУРА ТАБЛИЦЫ ASSIGNMENTS ===");

            Cursor cursor = db.rawQuery("PRAGMA table_info(assignments)", null);
            while (cursor.moveToNext()) {
                String columnName = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                String columnType = cursor.getString(cursor.getColumnIndexOrThrow("type"));
                Log.d("DatabaseDebug", "Столбец: " + columnName + " | Тип: " + columnType);
            }
            cursor.close();

        } catch (Exception e) {
            Log.e("DatabaseDebug", "❌ Ошибка проверки структуры таблицы: " + e.getMessage());
        }
    }

    // === ВНУТРЕННИЕ КЛАССЫ МОДЕЛЕЙ ДАННЫХ ===

    public static class User {
        private int id;
        private String name;
        private String email;
        private String role;
        private String brigade;
        private String position;
        private String avatarUrl;

        public User(int id, String name, String email, String role, String brigade, String position, String avatarUrl) {
            this.id = id;
            this.name = name;
            this.email = email;
            this.role = role;
            this.brigade = brigade;
            this.position = position;
            this.avatarUrl = avatarUrl;
        }

        // Геттеры
        public int getId() { return id; }
        public String getName() { return name; }
        public String getEmail() { return email; }
        public String getRole() { return role; }
        public String getBrigade() { return brigade; }
        public String getPosition() { return position; }
        public String getAvatarUrl() { return avatarUrl; }
    }

    public static class WorkerStats {
        public int completed = 0;
        public int defects = 0;
        public String todayAssignments = "";

        public double getDefectsPercent() {
            if (completed == 0) return 0.0;
            return (defects * 100.0) / completed;
        }
    }

    public static class WorkerDetailedStats {
        public int todayCompleted = 0;
        public int todayDefects = 0;
        public int todayAssignments = 0;
        public int weekCompleted = 0;
        public int weekDefects = 0;
        public List<Assignment> activeAssignments;
        public List<Assignment> recentCompleted;

        public double getTodayDefectPercent() {
            if (todayCompleted == 0) return 0.0;
            return (todayDefects * 100.0) / todayCompleted;
        }

        public double getWeekDefectPercent() {
            if (weekCompleted == 0) return 0.0;
            return (weekDefects * 100.0) / weekCompleted;
        }
    }

    public static class Assignment {
        public int id;
        public String operationName;
        public String productName;
        public int plannedQuantity;
        public int actualQuantity;
        public int defects;
        public String status;
        public String startTime;
        public String endTime;
        public int standardTime;

        public int getRemainingQuantity() {
            return plannedQuantity - actualQuantity;
        }
    }

    public static class QualityControlItem {
        public int id;
        public String workerName;
        public String operationName;
        public String productName;
        public int plannedQuantity;
        public int actualQuantity;
        public int defects;
        public String status;
        public String createdAt;
        public String startTime;
        public String endTime;
        public boolean qualityChecked;
        public int qualityCheckerId;
        public String qualityCheckDate;
        public String qualityNotes;

        public double getDefectPercentage() {
            if (actualQuantity == 0) return 0.0;
            return (defects * 100.0) / actualQuantity;
        }

        public String getStatusText() {
            if (qualityChecked) return "Проверено ✓";
            if ("completed".equals(status)) return "Ожидает проверки";
            if ("in_progress".equals(status)) return "В работе";
            return status != null ? status : "Неизвестно";
        }
    }

    public static class QualityStats {
        public int totalAssignments;
        public int checkedAssignments;
        public int totalCompleted;
        public int totalDefects;
        public int totalWorkers;
        public List<WorkerQualityStats> workerStats;

        public double getCheckPercentage() {
            if (totalAssignments == 0) return 0.0;
            return (checkedAssignments * 100.0) / totalAssignments;
        }

        public double getDefectPercentage() {
            if (totalCompleted == 0) return 0.0;
            return (totalDefects * 100.0) / totalCompleted;
        }
    }

    public static class WorkerQualityStats {
        public int workerId;
        public String workerName;
        public String position;
        public int totalAssignments;
        public int checkedAssignments;
        public int totalCompleted;
        public int totalDefects;

        public double getCheckPercentage() {
            if (totalAssignments == 0) return 0.0;
            return (checkedAssignments * 100.0) / totalAssignments;
        }

        public double getDefectPercentage() {
            if (totalCompleted == 0) return 0.0;
            return (totalDefects * 100.0) / totalCompleted;
        }
    }

    public static class MasterStats {
        public int workersCount = 0;
        public int totalCompleted = 0;
        public int totalDefects = 0;
        public List<Worker> workers;

        public double getDefectsPercent() {
            if (totalCompleted == 0) return 0.0;
            return (totalDefects * 100.0) / totalCompleted;
        }
    }

    public static class Worker {
        public int id;
        public String name;
        public String position;
        public int completed;

        public Worker() {}
        public Worker(int id, String name, String position, int completed) {
            this.id = id;
            this.name = name;
            this.position = position;
            this.completed = completed;
        }
    }

    public static class StorekeeperStats {
        public List<Material> lowStockMaterials = new ArrayList<>();
        public String recentUsage = "";
    }

    public static class Material {
        public String name;
        public double currentStock;
        public double minStock;
        public String unit;

        public Material() {}
        public Material(String name, double currentStock, double minStock, String unit) {
            this.name = name;
            this.currentStock = currentStock;
            this.minStock = minStock;
            this.unit = unit;
        }
    }

    public static class ManagerStats {
        public int totalOrders = 0;
        public int completedOrders = 0;
        public int inProgressOrders = 0;
        public String brigadePerformance = "";

        public int getCompletionPercent() {
            if (totalOrders == 0) return 0;
            return (completedOrders * 100) / totalOrders;
        }
    }

    public static class Order {
        public int id;
        public String orderNumber;
        public String customerName;
        public int productId;
        public int quantity;
        public String status;

        public Order() {}
    }

    public static class Product {
        public int id;
        public String article;
        public String name;

        public Product() {}
    }
}
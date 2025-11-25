package com.example.clothes;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import android.os.Handler;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

// Импорты внутренних классов DatabaseHelper
import com.example.clothes.DatabaseHelper.WorkerStats;
import com.example.clothes.DatabaseHelper.WorkerDetailedStats;
import com.example.clothes.DatabaseHelper.Assignment;
import com.example.clothes.DatabaseHelper.MasterStats;
import com.example.clothes.DatabaseHelper.Worker;
import com.example.clothes.DatabaseHelper.StorekeeperStats;
import com.example.clothes.DatabaseHelper.Material;
import com.example.clothes.DatabaseHelper.ManagerStats;
import com.example.clothes.DatabaseHelper.Order;
import com.example.clothes.DatabaseHelper.Product;
import com.example.clothes.DatabaseHelper.QualityControlItem;
import com.example.clothes.DatabaseHelper.QualityStats;
import com.example.clothes.DatabaseHelper.WorkerQualityStats;

public class DashboardActivity extends AppCompatActivity {

    private DatabaseHelper databaseHelper;
    private int userId;
    private String userRole;
    private String userBrigade;

    // Общие элементы для всех дашбордов
    private TextView tvWelcome;
    private TextView tvUserInfo;
    private Button btnLogout;

    // Элементы для worker dashboard
    private TextView tvPosition;
    private TextView tvCompletedCount;
    private TextView tvDefectsCount;
    private TextView tvDefectsPercent;
    private TextView tvOperation1;
    private TextView tvOperation2;
    private TextView tvOperation3;

    private Button btnQuickActions;
    private Button btnMyStats;
    private Button btnTodayTasks;

    // Элементы для master dashboard
    private TextView tvBrigade;
    private TextView tvWorkersCount;
    private TextView tvTotalCompleted;
    private TextView tvTotalDefects;
    private TextView tvDefectsPercentMaster;
    private TextView tvWorker1;
    private TextView tvWorker2;
    private TextView tvWorker3;

    // Элементы для storekeeper dashboard
    private TextView tvMaterial1;
    private TextView tvMaterial2;
    private TextView tvMaterial3;
    private TextView tvRecentUsage;

    // Элементы для manager dashboard
    private TextView tvTotalOrders;
    private TextView tvCompletedOrders;
    private TextView tvInProgressOrders;
    private TextView tvCompletionPercent;
    private TextView tvBrigadePerformance;

    // Форматтер для времени
    private SimpleDateFormat timeFormat;

    // Слушатель изменений данных
    private DatabaseHelper.OnDataChangedListener onDataChangedListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Инициализируем форматтер времени
        timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

        // Получаем данные пользователя
        Intent intent = getIntent();
        if (intent == null) {
            Log.e("DashboardActivity", "Intent is null");
            finish();
            return;
        }

        userRole = intent.getStringExtra("user_role");
        userId = intent.getIntExtra("user_id", -1);
        userBrigade = intent.getStringExtra("user_brigade");

        if (userRole == null) {
            Log.e("DashboardActivity", "User role is null");
            finish();
            return;
        }

        databaseHelper = new DatabaseHelper(this);

        // Устанавливаем слушатель изменений данных
        databaseHelper.setOnDataChangedListener(onDataChangedListener);

        // В зависимости от роли показываем разный интерфейс
        switch (userRole) {
            case "worker":
                setContentView(R.layout.activity_dashboard_worker);
                setupWorkerDashboard();
                break;
            case "master":
                setContentView(R.layout.activity_dashboard_master);
                setupMasterDashboard();
                break;
            case "storekeeper":
                setContentView(R.layout.activity_dashboard_storekeeper);
                setupStorekeeperDashboard();
                break;
            case "manager":
                setContentView(R.layout.activity_dashboard_manager);
                setupManagerDashboard();
                break;
            default:
                Log.w("DashboardActivity", "Unknown role: " + userRole);
                setContentView(R.layout.activity_dashboard);
                setupGeneralDashboard();
                break;
        }

        Log.d("Dashboard", "🎯 Открыт дашборд для роли: " + userRole);
    }

    // Инициализация слушателя изменений данных
    {
        onDataChangedListener = new DatabaseHelper.OnDataChangedListener() {
            @Override
            public void onWorkerStatsUpdated(int userId, DatabaseHelper.WorkerStats stats) {
                runOnUiThread(() -> {
                    if (DashboardActivity.this.userId == userId && "worker".equals(userRole)) {
                        loadWorkerData();
                        Log.d("DashboardActivity", "📊 Автообновление статистики работника");
                    }
                });
            }

            @Override
            public void onAssignmentsUpdated(int userId, List<DatabaseHelper.Assignment> assignments) {
                runOnUiThread(() -> {
                    if (DashboardActivity.this.userId == userId && "worker".equals(userRole)) {
                        loadWorkerData();
                        Log.d("DashboardActivity", "📝 Автообновление заданий работника");
                    }
                });
            }

            @Override
            public void onQualityCheckPerformed(int assignmentId) {
                runOnUiThread(() -> {
                    if ("master".equals(userRole)) {
                        loadMasterData();
                        Log.d("DashboardActivity", "✅ Автообновление после проверки качества");
                    } else if ("worker".equals(userRole)) {
                        loadWorkerData();
                    }
                });
            }

            @Override
            public void onAssignmentStatusChanged(int assignmentId, String newStatus) {
                runOnUiThread(() -> {
                    if ("worker".equals(userRole)) {
                        loadWorkerData();
                        Log.d("DashboardActivity", "🔄 Автообновление после изменения статуса задания");
                    } else if ("master".equals(userRole)) {
                        loadMasterData();
                    }
                });
            }
        };
    }

    // === ОБНОВЛЕННЫЙ КОНТРОЛЬ КАЧЕСТВА ===

    private void showQualityControlDialog() {
        new Thread(() -> {
            try {
                List<QualityControlItem> qualityTasks = databaseHelper.getQualityControlTasks();

                runOnUiThread(() -> {
                    try {
                        AlertDialog.Builder builder = new AlertDialog.Builder(this);
                        builder.setTitle("🔍 Контроль качества (" + qualityTasks.size() + " заданий)");

                        if (qualityTasks.isEmpty()) {
                            builder.setMessage("Нет заданий для проверки качества.\n\n" +
                                    "Задания появятся здесь когда:\n" +
                                    "✅ Статус задания = 'Выполнено'\n" +
                                    "✅ Флаг проверки = 'Не проверено'\n" +
                                    "✅ Есть выполненные работы\n\n" +
                                    "Проверить отладку?");

                            builder.setPositiveButton("Отладка", (dialog, which) -> {
                                debugQualityControlTasks();
                            });
                            builder.setNegativeButton("OK", null);
                            builder.show();
                            return;
                        }

                        // Создаем список заданий с возможностью выбора
                        String[] taskItems = new String[qualityTasks.size()];
                        for (int i = 0; i < qualityTasks.size(); i++) {
                            QualityControlItem task = qualityTasks.get(i);
                            String statusIcon = task.qualityChecked ? "✅" : "⏳";
                            String qualityStatus = task.qualityChecked ? "Проверено" : "Ожидает";
                            String workerInfo = task.workerName != null ? task.workerName : "Неизвестный работник";
                            String operationInfo = task.operationName != null ? task.operationName : "Неизвестная операция";
                            String statusInfo = "completed".equals(task.status) ? "Завершено" : "В работе";

                            taskItems[i] = String.format("%s %s - %s (%d/%d шт) - %s - %s",
                                    statusIcon, workerInfo, operationInfo,
                                    task.actualQuantity, task.plannedQuantity,
                                    statusInfo, qualityStatus);
                        }

                        builder.setItems(taskItems, (dialog, which) -> {
                            QualityControlItem selectedTask = qualityTasks.get(which);
                            showIndividualQualityCheckDialog(selectedTask);
                        });

                        builder.setPositiveButton("Проверить все", (dialog, which) -> {
                            showBulkQualityCheckDialog(qualityTasks);
                        });

                        builder.setNeutralButton("Отладка", (dialog, which) -> {
                            debugQualityControlTasks();
                        });

                        builder.setNegativeButton("Закрыть", null);
                        builder.show();

                    } catch (Exception e) {
                        Log.e("DashboardActivity", "Ошибка показа контроля качества: " + e.getMessage());
                        Toast.makeText(this, "Ошибка загрузки контроля качества", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                Log.e("DashboardActivity", "Ошибка получения данных качества: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, "Ошибка загрузки данных", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // Диалог индивидуальной проверки качества
    private void showIndividualQualityCheckDialog(QualityControlItem task) {
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Проверка качества");

            View dialogView = getLayoutInflater().inflate(R.layout.dialog_quality_check, null);
            builder.setView(dialogView);

            // Инициализация элементов
            TextView tvTaskDetails = dialogView.findViewById(R.id.tvTaskDetails);
            TextView tvWorkerInfo = dialogView.findViewById(R.id.tvWorkerInfo);
            TextView tvProductInfo = dialogView.findViewById(R.id.tvProductInfo);
            TextView tvProgressInfo = dialogView.findViewById(R.id.tvProgressInfo);
            TextView tvDefectsInfo = dialogView.findViewById(R.id.tvDefectsInfo);
            EditText etApprovedQuantity = dialogView.findViewById(R.id.etApprovedQuantity);
            EditText etDefectsFound = dialogView.findViewById(R.id.etDefectsFound);
            EditText etQualityNotes = dialogView.findViewById(R.id.etQualityNotes);
            Button btnApprove = dialogView.findViewById(R.id.btnApprove);
            Button btnReject = dialogView.findViewById(R.id.btnReject);

            // Заполняем данные
            if (tvTaskDetails != null) {
                tvTaskDetails.setText("Операция: " + (task.operationName != null ? task.operationName : "Не указана"));
            }
            if (tvWorkerInfo != null) {
                tvWorkerInfo.setText("👤 Работник: " + (task.workerName != null ? task.workerName : "Не указан"));
            }
            if (tvProductInfo != null) {
                tvProductInfo.setText("📦 Изделие: " + (task.productName != null ? task.productName : "Не указано"));
            }
            if (tvProgressInfo != null) {
                tvProgressInfo.setText("📊 Выполнено: " + task.actualQuantity + " шт");
            }
            if (tvDefectsInfo != null) {
                tvDefectsInfo.setText("❌ Текущий брак: " + task.defects + " шт");
            }

            // Устанавливаем текущие значения
            if (etApprovedQuantity != null) {
                etApprovedQuantity.setText(String.valueOf(task.actualQuantity));
            }
            if (etDefectsFound != null) {
                etDefectsFound.setText(String.valueOf(task.defects));
            }

            AlertDialog dialog = builder.create();

            // Обработчики кнопок
            if (btnApprove != null) {
                btnApprove.setOnClickListener(v -> {
                    String approvedStr = etApprovedQuantity.getText().toString();
                    String defectsStr = etDefectsFound.getText().toString();
                    String notes = etQualityNotes.getText().toString();

                    if (approvedStr.isEmpty()) {
                        Toast.makeText(this, "Введите одобренное количество", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    int approved = Integer.parseInt(approvedStr);
                    int defects = defectsStr.isEmpty() ? 0 : Integer.parseInt(defectsStr);

                    if (approved > task.actualQuantity) {
                        Toast.makeText(this, "Одобрено не может быть больше выполненного", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    performQualityCheck(task.id, userId, approved, defects, notes, true);
                    dialog.dismiss();
                });
            }

            if (btnReject != null) {
                btnReject.setOnClickListener(v -> {
                    String defectsStr = etDefectsFound.getText().toString();
                    String notes = etQualityNotes.getText().toString();

                    int defects = defectsStr.isEmpty() ? task.actualQuantity : Integer.parseInt(defectsStr);
                    performQualityCheck(task.id, userId, 0, defects, notes, false);
                    dialog.dismiss();
                });
            }

            dialog.show();

        } catch (Exception e) {
            Log.e("DashboardActivity", "Ошибка создания диалога проверки: " + e.getMessage());
            Toast.makeText(this, "Ошибка создания диалога", Toast.LENGTH_SHORT).show();
        }
    }

    // Массовая проверка качества всех заданий
    private void performBulkQualityCheck(List<QualityControlItem> tasks) {
        new Thread(() -> {
            try {
                int successCount = 0;
                int totalTasks = tasks.size();

                for (QualityControlItem task : tasks) {
                    if (!task.qualityChecked) {
                        boolean success = databaseHelper.checkAssignmentQuality(
                                task.id, userId, task.actualQuantity, task.defects, "Массовая проверка"
                        );
                        if (success) successCount++;

                        // Небольшая задержка для визуального эффекта
                        Thread.sleep(100);
                    }
                }

                final int finalSuccessCount = successCount;
                final int finalTotalTasks = totalTasks;

                runOnUiThread(() -> {
                    String message;
                    if (finalSuccessCount == finalTotalTasks) {
                        message = String.format("✅ Успешно проверено %d/%d заданий", finalSuccessCount, finalTotalTasks);
                    } else {
                        message = String.format("⚠️ Проверено %d/%d заданий", finalSuccessCount, finalTotalTasks);
                    }

                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();

                    // Обновляем данные на экране
                    if ("master".equals(userRole)) {
                        loadMasterData();
                    }

                    Log.d("DashboardActivity", "📊 Массовая проверка завершена: " + finalSuccessCount + "/" + finalTotalTasks);
                });
            } catch (Exception e) {
                Log.e("DashboardActivity", "❌ Ошибка массовой проверки: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, "Ошибка массовой проверки", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // Массовая проверка качества всех заданий конкретного работника
    private void performBulkWorkerQualityCheck(int workerId, String workerName) {
        new Thread(() -> {
            try {
                boolean success = databaseHelper.bulkCheckWorkerQuality(workerId, userId, "Массовая проверка работника " + workerName);

                runOnUiThread(() -> {
                    if (success) {
                        Toast.makeText(this, "✅ Все задания работника " + workerName + " проверены!", Toast.LENGTH_SHORT).show();
                        // Обновляем данные на экране
                        if ("master".equals(userRole)) {
                            loadMasterData();
                        }
                    } else {
                        Toast.makeText(this, "❌ Ошибка проверки заданий работника", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                Log.e("DashboardActivity", "❌ Ошибка массовой проверки работника: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, "Ошибка проверки работника", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
    // Метод для отладки - показывает информацию о заданиях
    private void debugQualityControlTasks() {
        new Thread(() -> {
            try {
                List<QualityControlItem> tasks = databaseHelper.getQualityControlTasks();

                runOnUiThread(() -> {
                    StringBuilder debugInfo = new StringBuilder();
                    debugInfo.append("🔍 ОТЛАДКА КОНТРОЛЯ КАЧЕСТВА:\n\n");
                    debugInfo.append("Всего заданий: ").append(tasks.size()).append("\n\n");

                    if (tasks.isEmpty()) {
                        debugInfo.append("Нет заданий для контроля качества.\n");
                        debugInfo.append("Проверьте:\n");
                        debugInfo.append("• Статус заданий (должен быть 'completed')\n");
                        debugInfo.append("• Флаг quality_checked (должен быть 0)\n");
                        debugInfo.append("• Наличие выполненных работ\n");
                    } else {
                        for (int i = 0; i < tasks.size(); i++) {
                            QualityControlItem task = tasks.get(i);
                            debugInfo.append(i + 1).append(". ").append(task.operationName)
                                    .append(" (").append(task.workerName).append(")\n")
                                    .append("   Статус: ").append(task.status)
                                    .append(", Проверено: ").append(task.qualityChecked ? "Да" : "Нет")
                                    .append(", Выполнено: ").append(task.actualQuantity).append("/").append(task.plannedQuantity)
                                    .append("\n\n");
                        }
                    }

                    new AlertDialog.Builder(this)
                            .setTitle("Отладка контроля качества")
                            .setMessage(debugInfo.toString())
                            .setPositiveButton("OK", null)
                            .show();
                });
            } catch (Exception e) {
                Log.e("DashboardActivity", "❌ Ошибка отладки: " + e.getMessage());
            }
        }).start();
    }
    // Диалог выбора работника для массовой проверки
    private void showWorkersQualityCheckDialog() {
        new Thread(() -> {
            try {
                List<Worker> workers = getBrigadeWorkers();
                List<String> workerNames = new ArrayList<>();
                List<Integer> workerIds = new ArrayList<>();

                // Собираем список работников с непроверенными заданиями
                for (Worker worker : workers) {
                    List<QualityControlItem> workerTasks = databaseHelper.getWorkerQualityControlTasks(worker.id);
                    int uncheckedCount = 0;
                    for (QualityControlItem task : workerTasks) {
                        if (!task.qualityChecked) {
                            uncheckedCount++;
                        }
                    }

                    if (uncheckedCount > 0) {
                        workerNames.add(worker.name + " (" + uncheckedCount + " непроверенных)");
                        workerIds.add(worker.id);
                    }
                }

                final List<Integer> finalWorkerIds = workerIds;
                final List<String> finalWorkerNames = new ArrayList<>();
                for (Worker worker : workers) {
                    finalWorkerNames.add(worker.name);
                }

                runOnUiThread(() -> {
                    if (workerNames.isEmpty()) {
                        Toast.makeText(this, "У всех работников все задания уже проверены", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Массовая проверка по работникам");
                    builder.setItems(workerNames.toArray(new String[0]), (dialog, which) -> {
                        int selectedWorkerId = finalWorkerIds.get(which);
                        String selectedWorkerName = finalWorkerNames.get(which);

                        // Подтверждение массовой проверки
                        new AlertDialog.Builder(this)
                                .setTitle("Подтверждение")
                                .setMessage("Проверить все задания работника " + selectedWorkerName + "?")
                                .setPositiveButton("Да", (d, w) -> {
                                    performBulkWorkerQualityCheck(selectedWorkerId, selectedWorkerName);
                                })
                                .setNegativeButton("Отмена", null)
                                .show();
                    });
                    builder.setNegativeButton("Отмена", null);
                    builder.show();
                });
            } catch (Exception e) {
                Log.e("DashboardActivity", "Ошибка показа диалога работников: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, "Ошибка загрузки данных работников", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
    // Массовая проверка качества
    private void showBulkQualityCheckDialog(List<QualityControlItem> tasks) {
        new Thread(() -> {
            try {
                List<QualityControlItem> uncheckedTasks = new ArrayList<>();
                for (QualityControlItem task : tasks) {
                    if (!task.qualityChecked) {
                        uncheckedTasks.add(task);
                    }
                }

                runOnUiThread(() -> {
                    if (uncheckedTasks.isEmpty()) {
                        Toast.makeText(this, "Все задания уже проверены", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Массовая проверка качества");
                    builder.setMessage("Будет проверено " + uncheckedTasks.size() + " заданий. Все задания будут одобрены с текущими количествами. Продолжить?");

                    builder.setPositiveButton("Проверить все", (dialog, which) -> {
                        performBulkQualityCheck(uncheckedTasks);
                    });

                    builder.setNegativeButton("Отмена", null);
                    builder.show();
                });
            } catch (Exception e) {
                Log.e("DashboardActivity", "Ошибка массовой проверки: " + e.getMessage());
            }
        }).start();
    }

    // Массовая проверка качества
    // Обновите метод performQualityCheck для лучшего логирования
    private void performQualityCheck(int assignmentId, int checkerId, int approvedQuantity, int defectsFound, String notes, boolean isApproved) {
        new Thread(() -> {
            try {
                Log.d("DashboardActivity", "🔍 Начало проверки качества для задания: " + assignmentId);

                boolean success = databaseHelper.checkAssignmentQuality(assignmentId, checkerId, approvedQuantity, defectsFound, notes);

                runOnUiThread(() -> {
                    if (success) {
                        String status = isApproved ? "одобрено" : "отклонено";
                        String message = String.format("Качество %s в %s!", status, getCurrentTime());
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();

                        // Обновляем данные на экране
                        if ("master".equals(userRole)) {
                            loadMasterData();
                            // Принудительно обновляем данные контроля качества
                            refreshQualityControlData();
                        } else if ("worker".equals(userRole)) {
                            loadWorkerData();
                        }

                        Log.d("DashboardActivity", "✅ Проверка качества завершена для задания: " + assignmentId);
                    } else {
                        Toast.makeText(this, "Ошибка проверки качества", Toast.LENGTH_SHORT).show();
                        Log.e("DashboardActivity", "❌ Ошибка проверки качества для задания: " + assignmentId);
                    }
                });
            } catch (Exception e) {
                Log.e("DashboardActivity", "❌ Ошибка проверки качества: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, "Ошибка проверки качества", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // Статистика качества
    private void showQualityStatistics() {
        new Thread(() -> {
            try {
                QualityStats stats = databaseHelper.getQualityStats();

                runOnUiThread(() -> {
                    try {
                        AlertDialog.Builder builder = new AlertDialog.Builder(this);
                        builder.setTitle("📊 Статистика качества");

                        StringBuilder message = new StringBuilder();
                        message.append("📈 ОБЩАЯ СТАТИСТИКА:\n");
                        message.append("• Всего заданий: ").append(stats.totalAssignments).append("\n");
                        message.append("• Проверено: ").append(stats.checkedAssignments).append(" (")
                                .append(String.format("%.1f", stats.getCheckPercentage())).append("%)\n");
                        message.append("• Выполнено: ").append(stats.totalCompleted).append(" шт\n");
                        message.append("• Брак: ").append(stats.totalDefects).append(" шт (")
                                .append(String.format("%.1f", stats.getDefectPercentage())).append("%)\n");
                        message.append("• Качество: ").append(String.format("%.1f", 100 - stats.getDefectPercentage())).append("%\n");
                        message.append("• Работников: ").append(stats.totalWorkers).append("\n\n");

                        message.append("👥 СТАТИСТИКА ПО РАБОТНИКАМ:\n");
                        if (stats.workerStats != null && !stats.workerStats.isEmpty()) {
                            for (WorkerQualityStats worker : stats.workerStats) {
                                message.append("🔹 ").append(worker.workerName).append(" (").append(worker.position).append(")\n");
                                message.append("   Проверено: ").append(worker.checkedAssignments)
                                        .append("/").append(worker.totalAssignments)
                                        .append(" (").append(String.format("%.1f", worker.getCheckPercentage())).append("%)\n");
                                message.append("   Качество: ").append(String.format("%.1f", 100 - worker.getDefectPercentage()))
                                        .append("%\n");
                                message.append("   Выполнено: ").append(worker.totalCompleted)
                                        .append(" шт, Брак: ").append(worker.totalDefects).append(" шт\n\n");
                            }
                        } else {
                            message.append("Нет данных по работникам\n");
                        }

                        builder.setMessage(message.toString());
                        builder.setPositiveButton("Обновить", (dialog, which) -> showQualityStatistics());
                        builder.setNegativeButton("Закрыть", null);
                        builder.show();

                    } catch (Exception e) {
                        Log.e("DashboardActivity", "Ошибка показа статистики: " + e.getMessage());
                        Toast.makeText(this, "Ошибка отображения статистики", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                Log.e("DashboardActivity", "Ошибка получения статистики: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, "Ошибка загрузки статистики", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void setupGeneralDashboard() {
        tvWelcome = findViewById(R.id.tvWelcome);
        tvUserInfo = findViewById(R.id.tvUserInfo);
        btnLogout = findViewById(R.id.btnLogout);

        Intent intent = getIntent();
        String userName = intent.getStringExtra("user_name");
        String userEmail = intent.getStringExtra("user_email");

        if (tvWelcome != null) {
            tvWelcome.setText("Добро пожаловать, " + userName + "!");
        }
        if (tvUserInfo != null) {
            tvUserInfo.setText(userName + "\n" + userEmail);
        }

        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> logout());
        }
    }

    // === WORKER DASHBOARD ===
    private void setupWorkerDashboard() {
        try {
            initWorkerViews();
            displayWorkerInfo();
            setupWorkerClickListeners();
            loadWorkerData();
        } catch (Exception e) {
            Log.e("DashboardActivity", "Ошибка инициализации worker: " + e.getMessage());
            Toast.makeText(this, "Ошибка загрузки интерфейса работника", Toast.LENGTH_SHORT).show();
        }
    }

    private void initWorkerViews() {
        tvWelcome = findViewById(R.id.tvWelcome);
        tvPosition = findViewById(R.id.tvPosition);
        tvCompletedCount = findViewById(R.id.tvCompletedCount);
        tvDefectsCount = findViewById(R.id.tvDefectsCount);
        tvDefectsPercent = findViewById(R.id.tvDefectsPercent);
        tvOperation1 = findViewById(R.id.tvOperation1);
        tvOperation2 = findViewById(R.id.tvOperation2);
        tvOperation3 = findViewById(R.id.tvOperation3);

        btnQuickActions = findViewById(R.id.btnQuickActions);
        btnMyStats = findViewById(R.id.btnMyStats);
        btnTodayTasks = findViewById(R.id.btnTodayTasks);
        btnLogout = findViewById(R.id.btnLogout);
    }

    private void displayWorkerInfo() {
        Intent intent = getIntent();
        if (intent != null) {
            String userName = intent.getStringExtra("user_name");
            String userPosition = intent.getStringExtra("user_position");

            if (tvWelcome != null) {
                tvWelcome.setText(userName != null ? userName : "Работник");
            }
            if (tvPosition != null) {
                tvPosition.setText("(" + (userPosition != null ? userPosition : "Работник") + ")");
            }
        }
    }

    private void loadWorkerData() {
        if (userId != -1) {
            new Thread(() -> {
                try {
                    WorkerStats stats = databaseHelper.getWorkerStats(userId);
                    WorkerDetailedStats detailedStats = databaseHelper.getWorkerDetailedStats(userId);

                    runOnUiThread(() -> {
                        try {
                            updateWorkerUI(stats, detailedStats);
                        } catch (Exception e) {
                            Log.e("DashboardActivity", "Ошибка обновления UI: " + e.getMessage());
                        }
                    });
                } catch (Exception e) {
                    Log.e("DashboardActivity", "Ошибка загрузки данных: " + e.getMessage());
                }
            }).start();
        }
    }

    private void updateWorkerUI(WorkerStats stats, WorkerDetailedStats detailedStats) {
        // Получаем текущее время
        String currentTime = getCurrentTime();

        // Обновляем основную статистику с реальным временем
        if (tvCompletedCount != null) {
            tvCompletedCount.setText(currentTime + " Выполнено: " + stats.completed + " шт");
        }
        if (tvDefectsCount != null) {
            tvDefectsCount.setText("Брак: " + stats.defects + " шт");
        }
        if (tvDefectsPercent != null) {
            tvDefectsPercent.setText(String.format("(%.1f%%)", stats.getDefectsPercent()));
        }

        // Обновляем активные задания
        if (stats.todayAssignments != null) {
            String[] assignments = stats.todayAssignments.split("\n");

            // Скрываем сообщение о отсутствии заданий по умолчанию
            TextView tvNoActiveTasks = findViewById(R.id.tvNoActiveTasks);
            if (tvNoActiveTasks != null) {
                tvNoActiveTasks.setVisibility(View.GONE);
            }

            // Отображаем задания или сообщение об их отсутствии
            if (assignments.length > 0 && !assignments[0].equals("Нет активных заданий")) {
                if (tvOperation1 != null && assignments.length > 0) {
                    tvOperation1.setText(assignments[0]);
                    setAssignmentStatusColor(tvOperation1, assignments[0]);
                }
                if (tvOperation2 != null && assignments.length > 1) {
                    tvOperation2.setText(assignments[1]);
                    setAssignmentStatusColor(tvOperation2, assignments[1]);
                }
                if (tvOperation3 != null && assignments.length > 2) {
                    tvOperation3.setText(assignments[2]);
                    setAssignmentStatusColor(tvOperation3, assignments[2]);
                }

                // Скрываем лишние TextView если заданий меньше 3
                if (assignments.length < 3 && tvOperation3 != null) {
                    tvOperation3.setVisibility(View.GONE);
                }
                if (assignments.length < 2 && tvOperation2 != null) {
                    tvOperation2.setVisibility(View.GONE);
                }
            } else {
                // Показываем сообщение об отсутствии активных заданий
                if (tvNoActiveTasks != null) {
                    tvNoActiveTasks.setVisibility(View.VISIBLE);
                }
                // Скрываем все TextView с заданиями
                if (tvOperation1 != null) tvOperation1.setVisibility(View.GONE);
                if (tvOperation2 != null) tvOperation2.setVisibility(View.GONE);
                if (tvOperation3 != null) tvOperation3.setVisibility(View.GONE);
            }
        }
    }

    // Метод для установки цвета в зависимости от статуса задания
    private void setAssignmentStatusColor(TextView textView, String assignmentText) {
        if (assignmentText.contains("(Назначено)")) {
            textView.setTextColor(getResources().getColor(android.R.color.darker_gray));
        } else if (assignmentText.contains("(В работе)")) {
            textView.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
        } else if (assignmentText.contains("(Выполнено)")) {
            textView.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        } else {
            textView.setTextColor(getResources().getColor(android.R.color.black));
        }
    }

    private void setupWorkerClickListeners() {
        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> logout());
        }

        if (btnQuickActions != null) {
            btnQuickActions.setOnClickListener(v -> {
                showRecordCompletionDialog();
            });
        }

        if (btnMyStats != null) {
            btnMyStats.setOnClickListener(v -> {
                showWorkerStatistics();
            });
        }

        if (btnTodayTasks != null) {
            btnTodayTasks.setOnClickListener(v -> {
                showTodayTasksWithStatusControl();
            });
        }
    }

    // Диалог учета выполнения
    private void showRecordCompletionDialog() {
        new Thread(() -> {
            try {
                List<Assignment> availableAssignments = databaseHelper.getAvailableAssignments(userId);

                runOnUiThread(() -> {
                    if (availableAssignments == null || availableAssignments.isEmpty()) {
                        Toast.makeText(this, "Нет доступных заданий для выполнения", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Учет выполнения");

                    // Создаем список заданий
                    String[] assignmentNames = new String[availableAssignments.size()];
                    for (int i = 0; i < availableAssignments.size(); i++) {
                        Assignment assignment = availableAssignments.get(i);
                        String statusText = assignment.status != null ? getStatusText(assignment.status) : "Назначено";
                        assignmentNames[i] = assignment.operationName + " - " +
                                assignment.actualQuantity + "/" + assignment.plannedQuantity + " шт" +
                                " (" + statusText + ")";
                    }

                    builder.setItems(assignmentNames, (dialog, which) -> {
                        Assignment selectedAssignment = availableAssignments.get(which);
                        showQuantityInputDialog(selectedAssignment);
                    });

                    builder.setNegativeButton("Отмена", null);
                    builder.show();
                });
            } catch (Exception e) {
                Log.e("DashboardActivity", "Ошибка в showRecordCompletionDialog: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, "Ошибка загрузки заданий", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // Диалог ввода количества
    private void showQuantityInputDialog(Assignment assignment) {
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Учет выполнения: " + assignment.operationName);

            View dialogView = getLayoutInflater().inflate(R.layout.dialog_record_completion, null);
            builder.setView(dialogView);

            EditText etQuantity = dialogView.findViewById(R.id.etQuantity);
            EditText etDefects = dialogView.findViewById(R.id.etDefects);
            TextView tvRemaining = dialogView.findViewById(R.id.tvRemaining);

            int remaining = assignment.getRemainingQuantity();
            if (tvRemaining != null) {
                tvRemaining.setText("Осталось: " + remaining + " шт");
            }
            if (etQuantity != null) {
                etQuantity.setHint("Макс: " + remaining);
            }

            builder.setPositiveButton("Учесть", (dialog, which) -> {
                try {
                    String quantityStr = etQuantity.getText().toString();
                    String defectsStr = etDefects.getText().toString();

                    if (quantityStr.isEmpty()) {
                        Toast.makeText(this, "Введите количество", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    int quantity = Integer.parseInt(quantityStr);
                    int defects = defectsStr.isEmpty() ? 0 : Integer.parseInt(defectsStr);

                    if (quantity > remaining) {
                        Toast.makeText(this, "Нельзя учесть больше чем осталось", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (defects > quantity) {
                        Toast.makeText(this, "Брак не может быть больше количества", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Сохраняем в БД
                    new Thread(() -> {
                        try {
                            boolean success = databaseHelper.recordOperationCompletion(assignment.id, quantity, defects);

                            runOnUiThread(() -> {
                                if (success) {
                                    Toast.makeText(this, "Выполнение учтено в " + getCurrentTime() + "!", Toast.LENGTH_SHORT).show();
                                    // Автоматически обновляем все данные
                                    loadWorkerData();
                                } else {
                                    Toast.makeText(this, "Ошибка учета выполнения", Toast.LENGTH_SHORT).show();
                                }
                            });
                        } catch (Exception e) {
                            Log.e("DashboardActivity", "Ошибка recordOperationCompletion: " + e.getMessage());
                            runOnUiThread(() -> Toast.makeText(this, "Ошибка сохранения", Toast.LENGTH_SHORT).show());
                        }
                    }).start();
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Введите корректные числа", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Log.e("DashboardActivity", "Ошибка в showQuantityInputDialog: " + e.getMessage());
                    Toast.makeText(this, "Ошибка обработки данных", Toast.LENGTH_SHORT).show();
                }
            });

            builder.setNegativeButton("Отмена", null);
            builder.show();
        } catch (Exception e) {
            Log.e("DashboardActivity", "Ошибка создания диалога: " + e.getMessage());
            Toast.makeText(this, "Ошибка создания диалога", Toast.LENGTH_SHORT).show();
        }
    }

    // Показать статистику работника
    private void showWorkerStatistics() {
        new Thread(() -> {
            try {
                WorkerDetailedStats stats = databaseHelper.getWorkerDetailedStats(userId);

                runOnUiThread(() -> {
                    try {
                        AlertDialog.Builder builder = new AlertDialog.Builder(this);
                        builder.setTitle("Моя статистика");

                        StringBuilder message = new StringBuilder();
                        message.append("📊 СЕГОДНЯ (").append(getCurrentTime()).append("):\n");
                        message.append("• Выполнено: ").append(stats.todayCompleted).append(" шт\n");
                        message.append("• Брак: ").append(stats.todayDefects).append(" шт\n");
                        message.append("• Качество: ").append(String.format("%.1f", 100 - stats.getTodayDefectPercent())).append("%\n\n");

                        message.append("📅 ЗА НЕДЕЛЮ:\n");
                        message.append("• Выполнено: ").append(stats.weekCompleted).append(" шт\n");
                        message.append("• Брак: ").append(stats.weekDefects).append(" шт\n");
                        message.append("• Качество: ").append(String.format("%.1f", 100 - stats.getWeekDefectPercent())).append("%\n\n");

                        int activeCount = (stats.activeAssignments != null) ? stats.activeAssignments.size() : 0;
                        message.append("🎯 АКТИВНЫЕ ЗАДАНИЯ: ").append(activeCount).append(" шт");

                        builder.setMessage(message.toString());
                        builder.setPositiveButton("OK", null);
                        builder.show();
                    } catch (Exception e) {
                        Log.e("DashboardActivity", "Ошибка показа статистики: " + e.getMessage());
                        Toast.makeText(this, "Ошибка отображения статистики", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                Log.e("DashboardActivity", "Ошибка получения статистики: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, "Ошибка загрузки статистики", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // Показать задания на сегодня с возможностью управления статусом
    private void showTodayTasksWithStatusControl() {
        new Thread(() -> {
            try {
                WorkerDetailedStats stats = databaseHelper.getWorkerDetailedStats(userId);

                runOnUiThread(() -> {
                    try {
                        AlertDialog.Builder builder = new AlertDialog.Builder(this);
                        builder.setTitle("Задания на сегодня (" + getCurrentTime() + ")");

                        StringBuilder message = new StringBuilder();

                        if (stats.activeAssignments != null && !stats.activeAssignments.isEmpty()) {
                            message.append("🚀 МОИ ЗАДАНИЯ:\n\n");
                            for (Assignment assignment : stats.activeAssignments) {
                                message.append("• ").append(assignment.operationName).append("\n");
                                message.append("  Продукт: ").append(assignment.productName != null ? assignment.productName : "Не указан").append("\n");
                                message.append("  Прогресс: ").append(assignment.actualQuantity).append("/").append(assignment.plannedQuantity).append(" шт\n");
                                message.append("  Статус: ").append(getStatusText(assignment.status)).append("\n\n");
                            }
                        } else {
                            message.append("Нет активных заданий на сегодня\n\n");
                        }

                        builder.setMessage(message.toString());

                        // Добавляем кнопки для управления статусом
                        builder.setPositiveButton("Обновить", (dialog, which) -> {
                            // Просто обновляем данные
                            loadWorkerData();
                        });

                        builder.setNeutralButton("Изменить статус", (dialog, which) -> {
                            showStatusChangeDialog(stats.activeAssignments);
                        });

                        builder.setNegativeButton("Закрыть", null);
                        builder.show();
                    } catch (Exception e) {
                        Log.e("DashboardActivity", "Ошибка показа заданий: " + e.getMessage());
                        Toast.makeText(this, "Ошибка отображения заданий", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                Log.e("DashboardActivity", "Ошибка получения заданий: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, "Ошибка загрузки заданий", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // Диалог изменения статуса задания
    private void showStatusChangeDialog(List<Assignment> assignments) {
        if (assignments == null || assignments.isEmpty()) {
            Toast.makeText(this, "Нет заданий для изменения статуса", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Изменить статус задания");

            // Создаем список заданий
            String[] assignmentNames = new String[assignments.size()];
            for (int i = 0; i < assignments.size(); i++) {
                Assignment assignment = assignments.get(i);
                assignmentNames[i] = assignment.operationName + " (" + getStatusText(assignment.status) + ")";
            }

            builder.setItems(assignmentNames, (dialog, which) -> {
                Assignment selectedAssignment = assignments.get(which);
                showStatusOptionsDialog(selectedAssignment);
            });

            builder.setNegativeButton("Отмена", null);
            builder.show();
        } catch (Exception e) {
            Log.e("DashboardActivity", "Ошибка изменения статуса: " + e.getMessage());
            Toast.makeText(this, "Ошибка изменения статуса", Toast.LENGTH_SHORT).show();
        }
    }

    // Диалог выбора нового статуса
    private void showStatusOptionsDialog(Assignment assignment) {
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Статус: " + assignment.operationName);

            // Используем только разрешенные статусы из БД
            String[] statusOptions = {"Взять в работу", "Выполнено", "Отменить"};
            String[] statusValues = {"in_progress", "completed", "cancelled"};

            builder.setItems(statusOptions, (dialog, which) -> {
                String newStatus = statusValues[which];
                updateAssignmentStatus(assignment.id, newStatus);
            });

            builder.setNegativeButton("Отмена", null);
            builder.show();
        } catch (Exception e) {
            Log.e("DashboardActivity", "Ошибка выбора статуса: " + e.getMessage());
            Toast.makeText(this, "Ошибка выбора статуса", Toast.LENGTH_SHORT).show();
        }
    }

    // Обновление статуса задания
    // Обновление статуса задания
    private void updateAssignmentStatus(int assignmentId, String newStatus) {
        new Thread(() -> {
            try {
                Log.d("DashboardActivity", "🔄 Попытка изменить статус задания " + assignmentId + " на: " + newStatus);

                boolean success = databaseHelper.updateAssignmentStatus(assignmentId, newStatus);

                runOnUiThread(() -> {
                    if (success) {
                        String message = "Статус обновлен в " + getCurrentTime() + "!";
                        if ("completed".equals(newStatus)) {
                            message += "\nЗадание теперь в контроле качества!";
                        }
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();

                        // Автоматически обновляем все данные
                        loadWorkerData();

                        // Если статус изменен на "выполнено", обновляем контроль качества
                        if ("completed".equals(newStatus) && "master".equals(userRole)) {
                            // Небольшая задержка для обновления БД
                            new Handler().postDelayed(() -> {
                                loadMasterData();
                            }, 500);
                        }
                    } else {
                        Toast.makeText(this, "Ошибка обновления статуса", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                Log.e("DashboardActivity", "❌ Ошибка обновления статуса: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, "Ошибка обновления статуса", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // === MASTER DASHBOARD ===
    private void setupMasterDashboard() {
        try {
            initMasterViews();
            displayMasterInfo();
            setupMasterClickListeners();
            loadMasterData();
        } catch (Exception e) {
            Log.e("DashboardActivity", "Ошибка инициализации мастера: " + e.getMessage());
            Toast.makeText(this, "Ошибка загрузки интерфейса мастера", Toast.LENGTH_SHORT).show();
        }
    }

    private void initMasterViews() {
        tvWelcome = findViewById(R.id.tvWelcome);
        tvBrigade = findViewById(R.id.tvBrigade);
        tvWorkersCount = findViewById(R.id.tvWorkersCount);
        tvTotalCompleted = findViewById(R.id.tvTotalCompleted);
        tvTotalDefects = findViewById(R.id.tvTotalDefects);
        tvDefectsPercentMaster = findViewById(R.id.tvDefectsPercent);
        tvWorker1 = findViewById(R.id.tvWorker1);
        tvWorker2 = findViewById(R.id.tvWorker2);
        tvWorker3 = findViewById(R.id.tvWorker3);

        // Инициализация кнопок
        Button btnBrigadeStats = findViewById(R.id.btnBrigadeStats);
        Button btnAssignTasks = findViewById(R.id.btnAssignTasks);
        Button btnQualityControl = findViewById(R.id.btnQualityControl);
        Button btnWorkSchedule = findViewById(R.id.btnWorkSchedule);
        btnLogout = findViewById(R.id.btnLogout);

        // ОБНОВЛЕННЫЙ ОБРАБОТЧИК ДЛЯ КОНТРОЛЯ КАЧЕСТВА
        if (btnQualityControl != null) {
            btnQualityControl.setOnClickListener(v -> showQualityControlDialog());
        }

        // Проверка на null для всех View
        if (tvWelcome == null || tvBrigade == null) {
            throw new IllegalStateException("Не все View найдены в макете");
        }
    }

    private void displayMasterInfo() {
        Intent intent = getIntent();
        String userName = intent.getStringExtra("user_name");

        if (tvWelcome != null) {
            tvWelcome.setText(userName);
        }
        if (tvBrigade != null) {
            tvBrigade.setText(userBrigade != null ? userBrigade : "Бригада №1");
        }
    }

    private void setupMasterClickListeners() {
        Button btnBrigadeStats = findViewById(R.id.btnBrigadeStats);
        Button btnAssignTasks = findViewById(R.id.btnAssignTasks);
        Button btnQualityControl = findViewById(R.id.btnQualityControl);
        Button btnWorkSchedule = findViewById(R.id.btnWorkSchedule);
        btnLogout = findViewById(R.id.btnLogout);

        // Настройка обработчиков кнопок
        if (btnBrigadeStats != null) {
            btnBrigadeStats.setOnClickListener(v -> showBrigadeStatistics());
        }

        if (btnAssignTasks != null) {
            btnAssignTasks.setOnClickListener(v -> showAssignTasksDialog());
        }

        // Контроль качества уже настроен в initMasterViews()

        if (btnWorkSchedule != null) {
            btnWorkSchedule.setOnClickListener(v -> showWorkSchedule());
        }

        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> logout());
        }
    }

    private void loadMasterData() {
        if (userId == -1 || userBrigade == null) {
            Log.e("DashboardActivity", "Неверные данные пользователя");
            showDefaultMasterData();
            return;
        }

        new Thread(() -> {
            try {
                MasterStats stats = databaseHelper.getMasterStats(userId, userBrigade);
                runOnUiThread(() -> {
                    try {
                        updateMasterUI(stats);
                    } catch (Exception e) {
                        Log.e("DashboardActivity", "Ошибка обновления UI мастера: " + e.getMessage());
                        showDefaultMasterData();
                    }
                });
            } catch (Exception e) {
                Log.e("DashboardActivity", "Ошибка загрузки данных мастера: " + e.getMessage());
                runOnUiThread(this::showDefaultMasterData);
            }
        }).start();
    }

    private void updateMasterUI(MasterStats stats) {
        if (stats == null) {
            showDefaultMasterData();
            return;
        }

        if (tvWorkersCount != null) {
            tvWorkersCount.setText("Работников: " + stats.workersCount);
        }
        if (tvTotalCompleted != null) {
            tvTotalCompleted.setText("Выполнено: " + stats.totalCompleted + " шт");
        }
        if (tvTotalDefects != null) {
            tvTotalDefects.setText("Брак: " + stats.totalDefects + " шт");
        }
        if (tvDefectsPercentMaster != null) {
            tvDefectsPercentMaster.setText(String.format("(%.1f%%)", stats.getDefectsPercent()));
        }

        // Безопасное обновление топ работников
        if (stats.workers != null && !stats.workers.isEmpty()) {
            if (tvWorker1 != null) {
                Worker worker1 = stats.workers.size() > 0 ? stats.workers.get(0) : null;
                tvWorker1.setText(worker1 != null ?
                        worker1.name + " - " + worker1.completed + " шт" : "Нет данных");
            }
            if (tvWorker2 != null) {
                Worker worker2 = stats.workers.size() > 1 ? stats.workers.get(1) : null;
                tvWorker2.setText(worker2 != null ?
                        worker2.name + " - " + worker2.completed + " шт" : "Нет данных");
            }
            if (tvWorker3 != null) {
                Worker worker3 = stats.workers.size() > 2 ? stats.workers.get(2) : null;
                tvWorker3.setText(worker3 != null ?
                        worker3.name + " - " + worker3.completed + " шт" : "Нет данных");
            }
        } else {
            // Установка значений по умолчанию
            if (tvWorker1 != null) tvWorker1.setText("Нет данных");
            if (tvWorker2 != null) tvWorker2.setText("Нет данных");
            if (tvWorker3 != null) tvWorker3.setText("Нет данных");
        }
    }

    private void showDefaultMasterData() {
        if (tvWorkersCount != null) tvWorkersCount.setText("Работников: 0");
        if (tvTotalCompleted != null) tvTotalCompleted.setText("Выполнено: 0 шт");
        if (tvTotalDefects != null) tvTotalDefects.setText("Брак: 0 шт");
        if (tvDefectsPercentMaster != null) tvDefectsPercentMaster.setText("(0.0%)");
        if (tvWorker1 != null) tvWorker1.setText("Нет данных");
        if (tvWorker2 != null) tvWorker2.setText("Нет данных");
        if (tvWorker3 != null) tvWorker3.setText("Нет данных");
    }

    // Статистика бригады
    private void showBrigadeStatistics() {
        new Thread(() -> {
            try {
                MasterStats stats = databaseHelper.getMasterStats(userId, userBrigade);
                List<Assignment> activeAssignments = getBrigadeActiveAssignments();

                runOnUiThread(() -> {
                    try {
                        AlertDialog.Builder builder = new AlertDialog.Builder(this);
                        builder.setTitle("📊 Статистика бригады " + userBrigade);

                        StringBuilder message = new StringBuilder();
                        message.append("👥 Состав бригады: ").append(stats.workersCount).append(" чел.\n");
                        message.append("✅ Выполнено: ").append(stats.totalCompleted).append(" шт\n");
                        message.append("❌ Брак: ").append(stats.totalDefects).append(" шт\n");
                        message.append("📈 Качество: ").append(String.format("%.1f", 100 - stats.getDefectsPercent())).append("%\n\n");

                        message.append("🏆 ЛУЧШИЕ РАБОТНИКИ:\n");
                        if (stats.workers != null && !stats.workers.isEmpty()) {
                            for (int i = 0; i < Math.min(3, stats.workers.size()); i++) {
                                Worker worker = stats.workers.get(i);
                                message.append(i + 1).append(". ").append(worker.name)
                                        .append(" - ").append(worker.completed).append(" шт\n");
                            }
                        } else {
                            message.append("Нет данных\n");
                        }

                        message.append("\n📝 АКТИВНЫЕ ЗАДАНИЯ: ").append(activeAssignments.size()).append(" шт\n");
                        if (!activeAssignments.isEmpty()) {
                            for (int i = 0; i < Math.min(3, activeAssignments.size()); i++) {
                                Assignment assignment = activeAssignments.get(i);
                                message.append("• ").append(assignment.operationName)
                                        .append(" - ").append(assignment.actualQuantity)
                                        .append("/").append(assignment.plannedQuantity).append(" шт\n");
                            }
                        } else {
                            message.append("Нет активных заданий\n");
                        }

                        builder.setMessage(message.toString());
                        builder.setPositiveButton("Обновить", (dialog, which) -> {
                            // Перезагружаем данные
                            loadMasterData();
                        });
                        builder.setNegativeButton("Закрыть", null);
                        builder.show();
                    } catch (Exception e) {
                        Log.e("DashboardActivity", "Ошибка показа статистики: " + e.getMessage());
                        Toast.makeText(this, "Ошибка отображения статистики", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                Log.e("DashboardActivity", "Ошибка получения статистики бригады: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, "Ошибка загрузки статистики", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // Назначение заданий
    private void showAssignTasksDialog() {
        new Thread(() -> {
            try {
                List<Worker> workers = getBrigadeWorkers();
                List<Assignment> availableOperations = getAvailableOperations();

                runOnUiThread(() -> {
                    try {
                        if (workers.isEmpty() || availableOperations.isEmpty()) {
                            Toast.makeText(this, "Нет доступных работников или операций", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        // Показываем диалог назначения
                        showTaskAssignmentDialog(workers, availableOperations);

                    } catch (Exception e) {
                        Log.e("DashboardActivity", "Ошибка показа диалога назначения: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                Log.e("DashboardActivity", "Ошибка получения данных для назначения: " + e.getMessage());
            }
        }).start();
    }

    // График работы
    private void showWorkSchedule() {
        new Thread(() -> {
            try {
                List<Worker> workers = getBrigadeWorkers();
                MasterStats stats = databaseHelper.getMasterStats(userId, userBrigade);

                runOnUiThread(() -> {
                    try {
                        AlertDialog.Builder builder = new AlertDialog.Builder(this);
                        builder.setTitle("📅 График работы бригады");

                        StringBuilder message = new StringBuilder();
                        message.append("Бригада: ").append(userBrigade).append("\n");
                        message.append("Дата: ").append(new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(new Date())).append("\n");
                        message.append("Общая выработка: ").append(stats.totalCompleted).append(" шт\n\n");

                        message.append("РАБОТНИКИ:\n");
                        if (workers != null && !workers.isEmpty()) {
                            for (Worker worker : workers) {
                                message.append("👤 ").append(worker.name).append("\n");
                                message.append("   Должность: ").append(worker.position).append("\n");
                                message.append("   Выполнено: ").append(worker.completed).append(" шт\n");
                                message.append("   Статус: ").append(worker.completed > 0 ? "Активен" : "Ожидание").append("\n\n");
                            }
                        } else {
                            message.append("Нет данных о работниках\n\n");
                        }

                        message.append("📊 ПРОИЗВОДИТЕЛЬНОСТЬ:\n");
                        message.append("• Средняя выработка: ").append(workers.isEmpty() ? 0 : stats.totalCompleted / workers.size()).append(" шт/чел\n");
                        message.append("• Уровень брака: ").append(String.format("%.1f", stats.getDefectsPercent())).append("%\n");
                        message.append("• Эффективность: ").append(String.format("%.1f", 100 - stats.getDefectsPercent())).append("%");

                        builder.setMessage(message.toString());

                        builder.setPositiveButton("Экспорт", (dialog, which) -> {
                            Toast.makeText(this, "Отчет экспортирован", Toast.LENGTH_SHORT).show();
                        });

                        builder.setNegativeButton("Закрыть", null);
                        builder.show();
                    } catch (Exception e) {
                        Log.e("DashboardActivity", "Ошибка показа графика работы: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                Log.e("DashboardActivity", "Ошибка получения графика работы: " + e.getMessage());
            }
        }).start();
    }

    // Диалог назначения задания
    private void showTaskAssignmentDialog(List<Worker> workers, List<Assignment> operations) {
        new Thread(() -> {
            try {
                List<Order> activeOrders = databaseHelper.getActiveOrders();

                runOnUiThread(() -> {
                    try {
                        AlertDialog.Builder builder = new AlertDialog.Builder(this);
                        builder.setTitle("📋 Назначение задания");

                        View dialogView = getLayoutInflater().inflate(R.layout.dialog_assign_task, null);
                        builder.setView(dialogView);

                        // Инициализация элементов диалога
                        TextView tvSelectedWorker = dialogView.findViewById(R.id.tvSelectedWorker);
                        TextView tvSelectedOperation = dialogView.findViewById(R.id.tvSelectedOperation);
                        TextView tvSelectedOrder = dialogView.findViewById(R.id.tvSelectedOrder);
                        EditText etQuantity = dialogView.findViewById(R.id.etQuantity);
                        Button btnSelectWorker = dialogView.findViewById(R.id.btnSelectWorker);
                        Button btnSelectOperation = dialogView.findViewById(R.id.btnSelectOperation);
                        Button btnSelectOrder = dialogView.findViewById(R.id.btnSelectOrder);

                        // Переменные для хранения выбора
                        final Worker[] selectedWorker = {null};
                        final Assignment[] selectedOperation = {null};
                        final Order[] selectedOrder = {null};

                        // Выбор работника
                        btnSelectWorker.setOnClickListener(v -> {
                            String[] workerNames = new String[workers.size()];
                            for (int i = 0; i < workers.size(); i++) {
                                workerNames[i] = workers.get(i).name + " (" + workers.get(i).position + ")";
                            }

                            new AlertDialog.Builder(this)
                                    .setTitle("Выберите работника")
                                    .setItems(workerNames, (dialog, which) -> {
                                        selectedWorker[0] = workers.get(which);
                                        tvSelectedWorker.setText(selectedWorker[0].name);
                                    })
                                    .setNegativeButton("Отмена", null)
                                    .show();
                        });

                        // Выбор операции
                        btnSelectOperation.setOnClickListener(v -> {
                            String[] operationNames = new String[operations.size()];
                            for (int i = 0; i < operations.size(); i++) {
                                operationNames[i] = operations.get(i).operationName +
                                        " (" + operations.get(i).standardTime + " мин/шт)";
                            }

                            new AlertDialog.Builder(this)
                                    .setTitle("Выберите операцию")
                                    .setItems(operationNames, (dialog, which) -> {
                                        selectedOperation[0] = operations.get(which);
                                        tvSelectedOperation.setText(selectedOperation[0].operationName);
                                    })
                                    .setNegativeButton("Отмена", null)
                                    .show();
                        });

                        // Выбор заказа
                        btnSelectOrder.setOnClickListener(v -> {
                            if (activeOrders.isEmpty()) {
                                Toast.makeText(this, "Нет доступных заказов", Toast.LENGTH_SHORT).show();
                                return;
                            }

                            String[] orderInfo = new String[activeOrders.size()];
                            for (int i = 0; i < activeOrders.size(); i++) {
                                Order order = activeOrders.get(i);
                                Product product = databaseHelper.getProductById(order.productId);
                                String productName = (product != null) ? product.name : "Неизвестный продукт";
                                orderInfo[i] = order.orderNumber + " - " + productName +
                                        " (" + order.quantity + " шт)";
                            }

                            new AlertDialog.Builder(this)
                                    .setTitle("Выберите заказ")
                                    .setItems(orderInfo, (dialog, which) -> {
                                        selectedOrder[0] = activeOrders.get(which);
                                        Product product = databaseHelper.getProductById(selectedOrder[0].productId);
                                        String productName = (product != null) ? product.name : "Неизвестный продукт";
                                        tvSelectedOrder.setText(selectedOrder[0].orderNumber + " - " + productName);
                                    })
                                    .setNegativeButton("Отмена", null)
                                    .show();
                        });

                        builder.setPositiveButton("Назначить", (dialog, which) -> {
                            // Проверка данных
                            if (selectedWorker[0] == null) {
                                Toast.makeText(this, "Выберите работника", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            if (selectedOperation[0] == null) {
                                Toast.makeText(this, "Выберите операцию", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            if (selectedOrder[0] == null) {
                                Toast.makeText(this, "Выберите заказ", Toast.LENGTH_SHORT).show();
                                return;
                            }

                            String quantityStr = etQuantity.getText().toString().trim();
                            if (quantityStr.isEmpty()) {
                                Toast.makeText(this, "Введите количество", Toast.LENGTH_SHORT).show();
                                return;
                            }

                            int quantity = Integer.parseInt(quantityStr);
                            if (quantity <= 0) {
                                Toast.makeText(this, "Количество должно быть больше 0", Toast.LENGTH_SHORT).show();
                                return;
                            }

                            // Назначаем задание
                            assignTaskToWorker(selectedWorker[0].id, selectedOperation[0].id,
                                    selectedOrder[0].id, quantity);
                        });

                        builder.setNegativeButton("Отмена", null);
                        builder.show();

                    } catch (Exception e) {
                        Log.e("DashboardActivity", "Ошибка показа диалога назначения: " + e.getMessage());
                        Toast.makeText(this, "Ошибка создания диалога", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                Log.e("DashboardActivity", "Ошибка получения заказов: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, "Ошибка загрузки данных", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // Метод для назначения задания
    private void assignTaskToWorker(int workerId, int operationId, int orderId, int plannedQuantity) {
        new Thread(() -> {
            try {
                boolean success = databaseHelper.assignTaskToWorker(workerId, operationId, orderId, plannedQuantity);

                runOnUiThread(() -> {
                    if (success) {
                        Toast.makeText(this, "✅ Задание успешно назначено!", Toast.LENGTH_SHORT).show();
                        // Обновляем данные на экране
                        loadMasterData();
                    } else {
                        Toast.makeText(this, "❌ Ошибка назначения задания", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                Log.e("DashboardActivity", "Ошибка назначения задания: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, "Ошибка назначения задания", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }


    // === ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ДЛЯ МАСТЕРА ===

    private List<Assignment> getBrigadeActiveAssignments() {
        try {
            return databaseHelper.getBrigadeActiveAssignments(userBrigade);
        } catch (Exception e) {
            Log.e("DashboardActivity", "Ошибка получения активных заданий: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<Worker> getBrigadeWorkers() {
        try {
            return databaseHelper.getBrigadeWorkers(userBrigade);
        } catch (Exception e) {
            Log.e("DashboardActivity", "Ошибка получения работников: " + e.getMessage());
            return getDefaultWorkers();
        }
    }

    private List<Worker> getDefaultWorkers() {
        List<Worker> workers = new ArrayList<>();
        workers.add(new Worker(-1, "Анна Петрова", "Швея", 127));
        workers.add(new Worker(-2, "Иван Сидоров", "Швец", 98));
        workers.add(new Worker(-3, "Мария Козлова", "Упаковщик", 156));
        return workers;
    }

    private List<Assignment> getAvailableOperations() {
        try {
            return databaseHelper.getAvailableOperations();
        } catch (Exception e) {
            Log.e("DashboardActivity", "Ошибка получения операций: " + e.getMessage());
            List<Assignment> operations = new ArrayList<>();

            Assignment op1 = new Assignment();
            op1.id = -1;
            op1.operationName = "Раскрой деталей";
            op1.standardTime = 5;
            operations.add(op1);

            Assignment op2 = new Assignment();
            op2.id = -2;
            op2.operationName = "Стачать швы";
            op2.standardTime = 8;
            operations.add(op2);

            Assignment op3 = new Assignment();
            op3.id = -3;
            op3.operationName = "Обработка";
            op3.standardTime = 6;
            operations.add(op3);

            return operations;
        }
    }
    // В классе DashboardActivity добавьте этот метод
    private void refreshQualityControlData() {
        if ("master".equals(userRole)) {
            new Thread(() -> {
                try {
                    // Принудительно обновляем данные контроля качества
                    List<QualityControlItem> qualityTasks = databaseHelper.getQualityControlTasks();

                    runOnUiThread(() -> {
                        Log.d("DashboardActivity", "🔄 Данные контроля качества обновлены: " +
                                qualityTasks.size() + " заданий");
                    });
                } catch (Exception e) {
                    Log.e("DashboardActivity", "❌ Ошибка обновления данных контроля качества: " + e.getMessage());
                }
            }).start();
        }
    }

    // === STOREKEEPER DASHBOARD ===
    private void setupStorekeeperDashboard() {
        try {
            tvWelcome = findViewById(R.id.tvWelcome);
            tvMaterial1 = findViewById(R.id.tvMaterial1);
            tvMaterial2 = findViewById(R.id.tvMaterial2);
            tvMaterial3 = findViewById(R.id.tvMaterial3);
            tvRecentUsage = findViewById(R.id.tvRecentUsage);
            btnLogout = findViewById(R.id.btnLogout);

            Intent intent = getIntent();
            String userName = intent.getStringExtra("user_name");

            if (tvWelcome != null) {
                tvWelcome.setText(userName);
            }

            // Загружаем данные из БД
            new Thread(() -> {
                try {
                    StorekeeperStats stats = databaseHelper.getStorekeeperStats();
                    runOnUiThread(() -> {
                        try {
                            // Материалы с низким запасом
                            if (stats.lowStockMaterials != null) {
                                if (tvMaterial1 != null && stats.lowStockMaterials.size() > 0) {
                                    Material m = stats.lowStockMaterials.get(0);
                                    tvMaterial1.setText(m.name + ": " + m.currentStock + " " + m.unit + " (мин: " + m.minStock + ")");
                                }
                                if (tvMaterial2 != null && stats.lowStockMaterials.size() > 1) {
                                    Material m = stats.lowStockMaterials.get(1);
                                    tvMaterial2.setText(m.name + ": " + m.currentStock + " " + m.unit + " (мин: " + m.minStock + ")");
                                }
                                if (tvMaterial3 != null && stats.lowStockMaterials.size() > 2) {
                                    Material m = stats.lowStockMaterials.get(2);
                                    tvMaterial3.setText(m.name + ": " + m.currentStock + " " + m.unit + " (мин: " + m.minStock + ")");
                                }
                            }

                            if (tvRecentUsage != null) {
                                tvRecentUsage.setText(stats.recentUsage);
                            }
                        } catch (Exception e) {
                            Log.e("DashboardActivity", "Ошибка обновления UI кладовщика: " + e.getMessage());
                        }
                    });
                } catch (Exception e) {
                    Log.e("DashboardActivity", "Ошибка загрузки данных кладовщика: " + e.getMessage());
                }
            }).start();

            if (btnLogout != null) {
                btnLogout.setOnClickListener(v -> logout());
            }
        } catch (Exception e) {
            Log.e("DashboardActivity", "Ошибка инициализации кладовщика: " + e.getMessage());
            Toast.makeText(this, "Ошибка загрузки интерфейса кладовщика", Toast.LENGTH_SHORT).show();
        }
    }

    // === MANAGER DASHBOARD ===
    private void setupManagerDashboard() {
        try {
            tvWelcome = findViewById(R.id.tvWelcome);
            tvTotalOrders = findViewById(R.id.tvTotalOrders);
            tvCompletedOrders = findViewById(R.id.tvCompletedOrders);
            tvInProgressOrders = findViewById(R.id.tvInProgressOrders);
            tvCompletionPercent = findViewById(R.id.tvCompletionPercent);
            tvBrigadePerformance = findViewById(R.id.tvBrigadePerformance);
            btnLogout = findViewById(R.id.btnLogout);

            Intent intent = getIntent();
            String userName = intent.getStringExtra("user_name");

            if (tvWelcome != null) {
                tvWelcome.setText(userName);
            }

            // Загружаем данные из БД
            new Thread(() -> {
                try {
                    ManagerStats stats = databaseHelper.getManagerStats();
                    runOnUiThread(() -> {
                        try {
                            if (tvTotalOrders != null) {
                                tvTotalOrders.setText("Всего заказов: " + stats.totalOrders);
                            }
                            if (tvCompletedOrders != null) {
                                tvCompletedOrders.setText("Выполнено: " + stats.completedOrders);
                            }
                            if (tvInProgressOrders != null) {
                                tvInProgressOrders.setText("В работе: " + stats.inProgressOrders);
                            }
                            if (tvCompletionPercent != null) {
                                tvCompletionPercent.setText("Выполнение: " + stats.getCompletionPercent() + "%");
                            }
                            if (tvBrigadePerformance != null) {
                                tvBrigadePerformance.setText(stats.brigadePerformance);
                            }
                        } catch (Exception e) {
                            Log.e("DashboardActivity", "Ошибка обновления UI менеджера: " + e.getMessage());
                        }
                    });
                } catch (Exception e) {
                    Log.e("DashboardActivity", "Ошибка загрузки данных менеджера: " + e.getMessage());
                }
            }).start();

            if (btnLogout != null) {
                btnLogout.setOnClickListener(v -> logout());
            }
        } catch (Exception e) {
            Log.e("DashboardActivity", "Ошибка инициализации менеджера: " + e.getMessage());
            Toast.makeText(this, "Ошибка загрузки интерфейса менеджера", Toast.LENGTH_SHORT).show();
        }
    }

    // === ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ===

    private String getCurrentTime() {
        return timeFormat.format(new Date());
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

    private void logout() {
        Toast.makeText(this, "Выход из системы", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (databaseHelper != null) {
            databaseHelper.close();
        }
    }
}
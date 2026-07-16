package com.nexus.agent.ui.planner

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.tabs.TabLayout
import com.nexus.agent.core.planner.TaskModel
import com.nexus.agent.core.planner.TaskPlanner
import com.nexus.agent.databinding.FragmentPlannerBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Fragment для управления задачами и планирования.
 * Отображает граф задач, карточки задач, лог выполнения и позволяет создавать/редактировать workflow.
 */
@AndroidEntryPoint
class PlannerFragment : Fragment() {

    private var _binding: FragmentPlannerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PlannerViewModel by viewModels()
    
    @Inject
    lateinit var taskPlanner: TaskPlanner

    private lateinit var taskAdapter: TaskAdapter
    private lateinit var taskGraphView: TaskGraphView
    private lateinit var executionLogView: ExecutionLogView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlannerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupViews()
        setupRecyclerView()
        setupGraphView()
        setupExecutionLog()
        setupFabMenu()
        observeViewModel()
        
        viewModel.loadTasks()
    }

    private fun setupViews() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> showListView()
                    1 -> showGraphView()
                    2 -> showLogView()
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        binding.searchEditText.setOnEditorActionListener { _, _, _ ->
            viewModel.searchTasks(binding.searchEditText.text?.toString() ?: "")
            true
        }

        binding.chipGroupStatus.setOnCheckedStateChangeListener { _, checkedIds ->
            val statuses = checkedIds.map { id ->
                when (id) {
                    com.nexus.agent.R.id.chip_pending -> TaskModel.Status.PENDING
                    com.nexus.agent.R.id.chip_running -> TaskModel.Status.RUNNING
                    com.nexus.agent.R.id.chip_completed -> TaskModel.Status.COMPLETED
                    com.nexus.agent.R.id.chip_failed -> TaskModel.Status.FAILED
                    else -> TaskModel.Status.PENDING
                }
            }
            viewModel.filterByStatuses(statuses)
        }

        binding.btnSort.setOnClickListener {
            showSortMenu()
        }
    }

    private fun setupRecyclerView() {
        taskAdapter = TaskAdapter(
            onTaskClick = { task ->
                showTaskDetail(task)
            },
            onTaskLongClick = { task ->
                showTaskOptions(task)
            },
            onExecuteClick = { task ->
                viewModel.executeTask(task.id)
            },
            onToggleExpand = { task ->
                viewModel.toggleTaskExpand(task.id)
            },
            onDependencyClick = { taskId ->
                viewModel.focusTask(taskId)
            }
        )

        binding.recyclerViewTasks.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = taskAdapter
        }
    }

    private fun setupGraphView() {
        taskGraphView = TaskGraphView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            onNodeClick = { taskId ->
                viewModel.getTask(taskId)?.let { showTaskDetail(it) }
            }
            onNodeLongClick = { taskId ->
                viewModel.getTask(taskId)?.let { showTaskOptions(it) }
            }
            onConnectionClick = { fromId, toId ->
                showDependencyDetail(fromId, toId)
            }
            onCanvasLongClick = { x, y ->
                showCreateTaskAtPosition(x, y)
            }
        }
        binding.graphContainer.addView(taskGraphView)
    }

    private fun setupExecutionLog() {
        executionLogView = ExecutionLogView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            onLogEntryClick = { entry ->
                viewModel.getTask(entry.taskId)?.let { showTaskDetail(it) }
            }
            onExportLogs = {
                viewModel.exportLogs()
            }
        }
        binding.logContainer.addView(executionLogView)
    }

    private fun setupFabMenu() {
        binding.fabAddTask.setOnClickListener {
            showCreateTaskDialog()
        }

        binding.fabExecuteAll.setOnClickListener {
            viewModel.executeAllPending()
        }

        binding.fabAutoPlan.setOnClickListener {
            showAutoPlanDialog()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.tasks.collectLatest { tasks ->
                        taskAdapter.submitList(tasks)
                        taskGraphView.setTasks(tasks)
                        binding.emptyState.isVisible = tasks.isEmpty()
                    }
                }

                launch {
                    viewModel.executionLogs.collectLatest { logs ->
                        executionLogView.setLogs(logs)
                    }
                }

                launch {
                    viewModel.isLoading.collectLatest { isLoading ->
                        binding.progressBar.isVisible = isLoading
                    }
                }

                launch {
                    viewModel.currentExecution.collectLatest { execution ->
                        execution?.let {
                            binding.executionStatusBar.isVisible = true
                            updateExecutionStatus(it)
                        } ?: run {
                            binding.executionStatusBar.isVisible = false
                        }
                    }
                }

                launch {
                    viewModel.events.collectLatest { event ->
                        when (event) {
                            is PlannerEvent.ShowToast -> {
                                Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
                            }
                            is PlannerEvent.TaskCreated -> {
                                Toast.makeText(requireContext(), "Задача создана", Toast.LENGTH_SHORT).show()
                            }
                            is PlannerEvent.ExecutionComplete -> {
                                Toast.makeText(requireContext(), 
                                    "Выполнение завершено: ${event.completed}/${event.total}", 
                                    Toast.LENGTH_LONG).show()
                            }
                            is PlannerEvent.CycleDetected -> {
                                Toast.makeText(requireContext(), 
                                    "Обнаружен цикл в зависимостях!", 
                                    Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }

                launch {
                    viewModel.stats.collectLatest { stats ->
                        binding.statsBar.updateStats(
                            totalTasks = stats.total,
                            pending = stats.pending,
                            running = stats.running,
                            completed = stats.completed,
                            failed = stats.failed
                        )
                    }
                }
            }
        }
    }

    private fun updateExecutionStatus(execution: ExecutionState) {
        binding.tvExecutionStatus.text = when (execution.phase) {
            ExecutionPhase.PLANNING -> "Планирование..."
            ExecutionPhase.EXECUTING -> "Выполнение: ${execution.currentTask?.name ?: ""}"
            ExecutionPhase.COMPLETED -> "Завершено"
            ExecutionPhase.FAILED -> "Ошибка: ${execution.error}"
        }
        
        binding.executionProgress.max = execution.total
        binding.executionProgress.progress = execution.completed
        binding.tvExecutionProgress.text = "${execution.completed}/${execution.total}"
        
        val color = when (execution.phase) {
            ExecutionPhase.PLANNING -> com.nexus.agent.R.color.status_planning
            ExecutionPhase.EXECUTING -> com.nexus.agent.R.color.status_running
            ExecutionPhase.COMPLETED -> com.nexus.agent.R.color.status_success
            ExecutionPhase.FAILED -> com.nexus.agent.R.color.status_failed
        }
        binding.executionStatusBar.setBackgroundColor(requireContext().getColor(color))
    }

    private fun showListView() {
        binding.recyclerViewTasks.isVisible = true
        binding.graphContainer.isVisible = false
        binding.logContainer.isVisible = false
    }

    private fun showGraphView() {
        binding.recyclerViewTasks.isVisible = false
        binding.graphContainer.isVisible = true
        binding.logContainer.isVisible = false
        taskGraphView.invalidate()
    }

    private fun showLogView() {
        binding.recyclerViewTasks.isVisible = false
        binding.graphContainer.isVisible = false
        binding.logContainer.isVisible = true
    }

    private fun showCreateTaskDialog(parentTaskId: String? = null) {
        val dialog = TaskEditDialog.newInstance(null, parentTaskId)
        dialog.onSave = { name, description, priority, dependencies, estimatedDuration ->
            viewModel.createTask(name, description, priority, dependencies, estimatedDuration)
        }
        dialog.show(childFragmentManager, "create_task")
    }

    private fun showCreateTaskAtPosition(x: Float, y: Float) {
        showCreateTaskDialog()
        viewModel.setPendingNodePosition(x, y)
    }

    private fun showTaskDetail(task: TaskModel) {
        val dialog = TaskDetailBottomSheet.newInstance(task)
        dialog.onAction = { action ->
            when (action) {
                TaskAction.EXECUTE -> viewModel.executeTask(task.id)
                TaskAction.EDIT -> showEditTaskDialog(task)
                TaskAction.DELETE -> viewModel.deleteTask(task.id)
                TaskAction.ADD_SUBTASK -> showCreateTaskDialog(parentTaskId = task.id)
                TaskAction.MARK_COMPLETE -> viewModel.markComplete(task.id)
                TaskAction.RETRY -> viewModel.retryTask(task.id)
            }
        }
        dialog.show(childFragmentManager, "task_detail")
    }

    private fun showEditTaskDialog(task: TaskModel) {
        val dialog = TaskEditDialog.newInstance(task)
        dialog.onSave = { name, description, priority, dependencies, estimatedDuration ->
            viewModel.updateTask(task.id, name, description, priority, dependencies, estimatedDuration)
        }
        dialog.show(childFragmentManager, "edit_task")
    }

    private fun showTaskOptions(task: TaskModel) {
        val bottomSheet = TaskOptionsBottomSheet.newInstance(task)
        bottomSheet.onAction = { action ->
            when (action) {
                TaskAction.EXECUTE -> viewModel.executeTask(task.id)
                TaskAction.EDIT -> showEditTaskDialog(task)
                TaskAction.DELETE -> viewModel.deleteTask(task.id)
                TaskAction.ADD_SUBTASK -> showCreateTaskDialog(parentTaskId = task.id)
                TaskAction.MARK_COMPLETE -> viewModel.markComplete(task.id)
                TaskAction.RETRY -> viewModel.retryTask(task.id)
                TaskAction.DUPLICATE -> viewModel.duplicateTask(task.id)
            }
        }
        bottomSheet.show(childFragmentManager, "task_options")
    }

    private fun showDependencyDetail(fromId: String, toId: String) {
        val from = viewModel.getTask(fromId)?.name ?: fromId
        val to = viewModel.getTask(toId)?.name ?: toId
        Toast.makeText(requireContext(), "$from → $to", Toast.LENGTH_SHORT).show()
    }

    private fun showSortMenu() {
        val bottomSheet = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(com.nexus.agent.R.layout.bottom_sheet_sort, null)
        
        view.findViewById<View>(com.nexus.agent.R.id.sort_by_priority).setOnClickListener {
            viewModel.sortBy(SortCriteria.PRIORITY)
            bottomSheet.dismiss()
        }
        view.findViewById<View>(com.nexus.agent.R.id.sort_by_date).setOnClickListener {
            viewModel.sortBy(SortCriteria.DATE)
            bottomSheet.dismiss()
        }
        view.findViewById<View>(com.nexus.agent.R.id.sort_by_status).setOnClickListener {
            viewModel.sortBy(SortCriteria.STATUS)
            bottomSheet.dismiss()
        }
        view.findViewById<View>(com.nexus.agent.R.id.sort_by_name).setOnClickListener {
            viewModel.sortBy(SortCriteria.NAME)
            bottomSheet.dismiss()
        }
        
        bottomSheet.setContentView(view)
        bottomSheet.show()
    }

    private fun showAutoPlanDialog() {
        val dialog = AutoPlanDialog()
        dialog.onPlan = { description ->
            viewModel.autoPlan(description)
        }
        dialog.show(childFragmentManager, "auto_plan")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

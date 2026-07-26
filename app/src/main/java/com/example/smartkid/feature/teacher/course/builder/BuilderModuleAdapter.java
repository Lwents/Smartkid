package com.example.smartkid.feature.teacher.course.builder;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartkid.R;
import com.example.smartkid.data.model.FeatureItem;

import java.util.List;

/** Outer adapter: các chương. Mỗi chương có RecyclerView lồng chứa bài học. */
final class BuilderModuleAdapter extends RecyclerView.Adapter<BuilderModuleAdapter.ModuleViewHolder> {

    interface Listener {
        void onModuleDragStart(RecyclerView.ViewHolder holder);
        void onModuleMenuClicked(BuilderModule module);
        void onLessonClicked(String moduleId, FeatureItem lesson);
        void onAddLesson(BuilderModule module, String title, EditText input, ProgressBar progress);
        void onLessonReordered(BuilderModule module);
    }

    private final List<BuilderModule> modules;
    private final Listener listener;

    BuilderModuleAdapter(List<BuilderModule> modules, Listener listener) {
        this.modules = modules;
        this.listener = listener;
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        return modules.get(position).id.hashCode();
    }

    @NonNull
    @Override
    public ModuleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.teacher_item_builder_module, parent, false);
        return new ModuleViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ModuleViewHolder holder, int position) {
        BuilderModule module = modules.get(position);
        holder.title.setText(module.title);

        BuilderLessonAdapter lessonAdapter = new BuilderLessonAdapter(
                module.id, module.lessons, new BuilderLessonAdapter.Listener() {
            @Override
            public void onLessonClicked(String moduleId, FeatureItem lesson) {
                if (listener != null) listener.onLessonClicked(moduleId, lesson);
            }

            @Override
            public void onLessonDragStart(RecyclerView.ViewHolder vh) {
                if (holder.lessonTouchHelper != null) holder.lessonTouchHelper.startDrag(vh);
            }
        });
        holder.lessonsRecycler.setLayoutManager(
                new LinearLayoutManager(holder.itemView.getContext()));
        holder.lessonsRecycler.setAdapter(lessonAdapter);
        holder.lessonsRecycler.setNestedScrollingEnabled(false);

        ItemTouchHelper touchHelper = new ItemTouchHelper(
                new LessonReorderCallback(module, lessonAdapter, listener));
        touchHelper.attachToRecyclerView(holder.lessonsRecycler);
        holder.lessonTouchHelper = touchHelper;

        holder.menu.setOnClickListener(v -> {
            if (listener != null) listener.onModuleMenuClicked(module);
        });
        holder.handle.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN && listener != null) {
                listener.onModuleDragStart(holder);
            }
            return false;
        });

        holder.lessonInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean isDone = actionId == EditorInfo.IME_ACTION_DONE;
            boolean isEnterKey = event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (isDone || isEnterKey) {
                String title = holder.lessonInput.getText() == null ? ""
                        : holder.lessonInput.getText().toString().trim();
                if (!TextUtils.isEmpty(title) && listener != null) {
                    listener.onAddLesson(module, title, holder.lessonInput, holder.lessonProgress);
                }
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return modules.size();
    }

    /** Kéo-thả bài học trong phạm vi một chương (không cho sang chương khác). */
    private static final class LessonReorderCallback extends ItemTouchHelper.Callback {
        private final BuilderModule module;
        private final BuilderLessonAdapter adapter;
        private final Listener listener;
        private boolean moved;

        LessonReorderCallback(BuilderModule module, BuilderLessonAdapter adapter,
                              Listener listener) {
            this.module = module;
            this.adapter = adapter;
            this.listener = listener;
        }

        @Override
        public boolean isLongPressDragEnabled() {
            return false;
        }

        @Override
        public int getMovementFlags(@NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder) {
            return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
        }

        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView,
                              @NonNull RecyclerView.ViewHolder viewHolder,
                              @NonNull RecyclerView.ViewHolder target) {
            int from = viewHolder.getBindingAdapterPosition();
            int to = target.getBindingAdapterPosition();
            if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false;
            java.util.Collections.swap(module.lessons, from, to);
            adapter.notifyItemMoved(from, to);
            moved = true;
            return true;
        }

        @Override
        public void clearView(@NonNull RecyclerView recyclerView,
                              @NonNull RecyclerView.ViewHolder viewHolder) {
            super.clearView(recyclerView, viewHolder);
            if (moved && listener != null) {
                listener.onLessonReordered(module);
                moved = false;
            }
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        }
    }

    static final class ModuleViewHolder extends RecyclerView.ViewHolder {
        final ImageView handle;
        final TextView title;
        final ImageView menu;
        final RecyclerView lessonsRecycler;
        final EditText lessonInput;
        final ProgressBar lessonProgress;
        ItemTouchHelper lessonTouchHelper;

        ModuleViewHolder(@NonNull View itemView) {
            super(itemView);
            handle = itemView.findViewById(R.id.handleTeacherBuilderModule);
            title = itemView.findViewById(R.id.textTeacherBuilderModuleTitle);
            menu = itemView.findViewById(R.id.buttonTeacherBuilderModuleMenu);
            lessonsRecycler = itemView.findViewById(R.id.recyclerTeacherBuilderLessons);
            lessonInput = itemView.findViewById(R.id.editTeacherBuilderLessonInput);
            lessonProgress = itemView.findViewById(R.id.progressTeacherBuilderLessonInput);
        }
    }
}

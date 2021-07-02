package org.openbot.logging;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.CheckBox;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.openbot.databinding.ItemSensorBinding;
import org.openbot.env.SharedPreferencesManager;

public class SensorListAdapter extends RecyclerView.Adapter<SensorListAdapter.ViewHolder> {

  private final List<String> names;
  private final List<Boolean> status;
  private final SharedPreferencesManager preferencesManager;

  public SensorListAdapter(
      HashMap<String, Boolean> items, SharedPreferencesManager preferencesManager) {
    names = new ArrayList<>(items.keySet());
    status = new ArrayList<>(items.values());
    this.preferencesManager = preferencesManager;
  }

  @NotNull
  @Override
  public ViewHolder onCreateViewHolder(@NotNull ViewGroup parent, int viewType) {
    return new ViewHolder(
        ItemSensorBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
  }

  @Override
  public void onBindViewHolder(final ViewHolder holder, int position) {
    holder.checkBox.setText(names.get(position));
    holder.checkBox.setChecked(status.get(position));
    holder.checkBox.setOnClickListener(
        v -> preferencesManager.setSensorStatus(holder.checkBox.isChecked(), names.get(position)));
  }

  @Override
  public int getItemCount() {
    return names.size();
  }

  public static class ViewHolder extends RecyclerView.ViewHolder {
    public CheckBox checkBox;

    public ViewHolder(ItemSensorBinding binding) {
      super(binding.getRoot());
      checkBox = binding.check;
    }
  }
}

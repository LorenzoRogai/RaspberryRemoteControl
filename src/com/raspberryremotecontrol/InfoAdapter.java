/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.raspberryremotecontrol;

import android.app.Activity;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.content.*;
import android.content.Context;
import android.graphics.drawable.*;
import java.util.*;
/**
 *
 * @author Lorenzo
 */
public class InfoAdapter extends ArrayAdapter<Info> {

    Context context;
    int layoutResourceId;
    List<Info> data = null;

    public InfoAdapter(Context context, int layoutResourceId, List<Info> data) {
        super(context, layoutResourceId, data);
        this.layoutResourceId = layoutResourceId;
        this.context = context;
        this.data = data;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View row = convertView;
        InfoHolder holder = null;

        if (row == null) {
            LayoutInflater inflater = ((Activity) context).getLayoutInflater();
            row = inflater.inflate(layoutResourceId, parent, false);

            holder = new InfoHolder();
            holder.imgIcon = (ImageView) row.findViewById(R.id.imgIcon);
            holder.txtName = (TextView) row.findViewById(R.id.txtTitle);
            holder.txtDescription = (TextView) row.findViewById(R.id.txtDesc);
            holder.progressBar = (TextProgressBar) row.findViewById(R.id.ProgressBar);

            row.setTag(holder);
        } else {
            holder = (InfoHolder) row.getTag();
        }

        Info info = data.get(position);
        holder.txtName.setText(info.Name);
        holder.imgIcon.setImageResource(info.Icon);
        holder.txtDescription.setText(info.Description);
        if (info.ProgressBarProgress > -1) {
            if (holder.progressBar.getVisibility() == View.GONE) {
                holder.progressBar.setVisibility(View.VISIBLE);
            }
            if (holder.progressBar.getProgress() != info.ProgressBarProgress) {

                holder.progressBar.setText(info.ProgressBarProgress + "%");
                holder.progressBar.setTextSize(16);
                holder.progressBar.setProgress(info.ProgressBarProgress);
            }
        } else {
            holder.progressBar.setVisibility(View.GONE);
        }

        return row;
    }

    public void Update(List<Info> data) {
        this.data = data;
        notifyDataSetChanged();
    }

    static class InfoHolder {

        ImageView imgIcon;
        TextView txtName;
        TextView txtDescription;
        TextProgressBar progressBar;
    }
}

/*
 * Copyright (C) 2026 Ammar Siddiqui
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.deskclock.challenges.ui.config

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.android.deskclock.R
import com.android.deskclock.challenges.ChallengeConfig
import com.android.deskclock.challenges.ChallengeSummary
import java.util.Collections

/**
 * The configured challenges, in the order they will run at ring time.
 *
 * Order is the point of the list, so rows can be dragged. Each row opens its own settings
 * and can be removed.
 */
class ChallengeListAdapter(
    private val challenges: MutableList<ChallengeConfig>,
    private val onEdit: (Int) -> Unit,
    private val onRemove: (Int) -> Unit,
    private val onReordered: () -> Unit,
) : RecyclerView.Adapter<ChallengeListAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.challenge_item_icon)
        val name: TextView = view.findViewById(R.id.challenge_item_name)
        val summary: TextView = view.findViewById(R.id.challenge_item_summary)
        val remove: ImageView = view.findViewById(R.id.challenge_item_remove)
        val position: TextView = view.findViewById(R.id.challenge_item_position)
    }

    override fun getItemCount(): Int = challenges.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.challenge_list_item, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val config = challenges[position]
        val context = holder.itemView.context

        holder.icon.setImageResource(ChallengeSummary.iconOf(config.kind))
        holder.name.setText(ChallengeSummary.nameOf(config.kind))
        holder.summary.text = ChallengeSummary.describe(context, config)
        // Shows the running order, which is what the drag handle changes.
        holder.position.text = (position + 1).toString()

        holder.itemView.setOnClickListener { onEdit(holder.bindingAdapterPosition) }
        holder.remove.setOnClickListener { onRemove(holder.bindingAdapterPosition) }
        holder.remove.contentDescription = context.getString(R.string.challenge_remove)
    }

    /** Long press and drag to reorder; there is no swipe, so a row cannot vanish by accident. */
    val dragCallback: ItemTouchHelper.Callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder,
        ): Boolean {
            val from = viewHolder.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            if (from !in challenges.indices || to !in challenges.indices) return false
            Collections.swap(challenges, from, to)
            notifyItemMoved(from, to)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            // Refresh once the drag settles so the position numbers match the new order.
            onReordered()
        }
    }
}

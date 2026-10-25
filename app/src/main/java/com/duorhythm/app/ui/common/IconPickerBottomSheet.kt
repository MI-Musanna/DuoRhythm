package com.duorhythm.app.ui.common

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.duorhythm.app.R
import com.duorhythm.app.databinding.DialogIconPickerBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import java.util.Locale

class IconPickerBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DialogIconPickerBinding? = null
    private val binding get() = _binding!!

    private var currentlySelectedIcon: String = "ic_wallet"
    private var onIconSelectedListener: ((String) -> Unit)? = null

    private lateinit var iconAdapter: IconGridAdapter
    private var selectedCategory: String = "All"
    private var currentSearchQuery: String = ""

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let { sheet ->
                val behavior = BottomSheetBehavior.from(sheet)
                val displayMetrics = resources.displayMetrics
                val targetPeek = (displayMetrics.heightPixels * 0.75).toInt()
                behavior.peekHeight = targetPeek
                behavior.state = BottomSheetBehavior.STATE_COLLAPSED
                behavior.isHideable = true
            }
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogIconPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnCloseIconPicker.setOnClickListener {
            dismiss()
        }

        setupCategories()
        setupRecyclerView()
        setupSearch()
        filterIcons()
    }

    private fun setupCategories() {
        val categories = listOf("All", "Finance", "Academic", "Lifestyle", "Tech", "Transport", "Shopping", "Food", "Health", "Entertainment")
        binding.chipGroupCategories.removeAllViews()

        for (category in categories) {
            val chip = Chip(requireContext()).apply {
                text = category
                isCheckable = true
                isChecked = (category == "All")
                chipBackgroundColor = ContextCompat.getColorStateList(requireContext(), R.color.chip_choice_background)
                setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.chip_choice_text))
                chipStrokeColor = ContextCompat.getColorStateList(requireContext(), R.color.chip_choice_stroke)
                chipStrokeWidth = 1f * resources.displayMetrics.density
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedCategory = category
                        filterIcons()
                    }
                }
            }
            binding.chipGroupCategories.addView(chip)
        }
    }

    private fun setupRecyclerView() {
        iconAdapter = IconGridAdapter(currentlySelectedIcon) { selectedItem ->
            onIconSelectedListener?.invoke(selectedItem.id)
            dismiss()
        }
        binding.rvIcons.apply {
            layoutManager = GridLayoutManager(requireContext(), 5)
            adapter = iconAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupSearch() {
        binding.btnClearSearch.setOnClickListener {
            binding.etSearchIcons.setText("")
        }

        binding.etSearchIcons.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s?.toString()?.trim().orEmpty()
                binding.btnClearSearch.visibility = if (currentSearchQuery.isNotEmpty()) View.VISIBLE else View.GONE
                filterIcons()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterIcons() {
        val query = currentSearchQuery.lowercase(Locale.US)
        val all = AppIconRegistry.getAllIcons()

        val filtered = all.filter { icon ->
            val matchesCategory = when (selectedCategory) {
                "All" -> true
                "Lifestyle" -> icon.category == "Shopping" || icon.category == "Food" || icon.category == "Entertainment"
                else -> icon.category.equals(selectedCategory, ignoreCase = true)
            }

            val matchesQuery = if (query.isEmpty()) {
                true
            } else {
                icon.title.lowercase(Locale.US).contains(query) ||
                icon.id.lowercase(Locale.US).contains(query) ||
                icon.keywords.lowercase(Locale.US).contains(query) ||
                icon.category.lowercase(Locale.US).contains(query)
            }

            matchesCategory && matchesQuery
        }

        iconAdapter.submitList(filtered)
        binding.tvEmptyIcons.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.rvIcons.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "IconPickerBottomSheet"

        fun show(
            fragmentManager: FragmentManager,
            selectedIcon: String = "ic_wallet",
            onIconSelected: (String) -> Unit
        ): IconPickerBottomSheet {
            val picker = IconPickerBottomSheet().apply {
                this.currentlySelectedIcon = selectedIcon
                this.onIconSelectedListener = onIconSelected
            }
            picker.show(fragmentManager, TAG)
            return picker
        }
    }

    private class IconGridAdapter(
        private val selectedIconId: String,
        private val onIconClicked: (AppIconRegistry.IconItem) -> Unit
    ) : RecyclerView.Adapter<IconGridAdapter.IconViewHolder>() {

        private var items: List<AppIconRegistry.IconItem> = emptyList()

        fun submitList(newItems: List<AppIconRegistry.IconItem>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IconViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_icon_picker, parent, false)
            return IconViewHolder(view)
        }

        override fun onBindViewHolder(holder: IconViewHolder, position: Int) {
            val item = items[position]
            holder.bind(item, selectedIconId, onIconClicked)
        }

        override fun getItemCount(): Int = items.size

        class IconViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val card: MaterialCardView = itemView.findViewById(R.id.cardIconSurface)
            private val ivIcon: ImageView = itemView.findViewById(R.id.ivIconItem)

            fun bind(
                item: AppIconRegistry.IconItem,
                selectedId: String,
                onClicked: (AppIconRegistry.IconItem) -> Unit
            ) {
                val context = itemView.context
                ivIcon.setImageResource(item.resId)
                itemView.contentDescription = item.title

                val isSelected = item.id.equals(selectedId, ignoreCase = true) ||
                        item.id.equals("ic_$selectedId", ignoreCase = true) ||
                        item.id.removePrefix("ic_").equals(selectedId.removePrefix("ic_"), ignoreCase = true)

                if (isSelected) {
                    card.setCardBackgroundColor(Color.parseColor("#1E293B"))
                    card.strokeColor = ContextCompat.getColor(context, R.color.primary)
                    card.strokeWidth = (2f * context.resources.displayMetrics.density).toInt()
                    ivIcon.setColorFilter(ContextCompat.getColor(context, R.color.primary))
                } else {
                    card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.surface_variant))
                    card.strokeColor = ContextCompat.getColor(context, R.color.card_stroke)
                    card.strokeWidth = (1f * context.resources.displayMetrics.density).toInt()
                    ivIcon.setColorFilter(ContextCompat.getColor(context, R.color.text_primary))
                }

                card.setOnClickListener {
                    onClicked(item)
                }
            }
        }
    }
}

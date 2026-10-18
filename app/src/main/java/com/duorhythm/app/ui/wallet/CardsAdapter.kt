package com.duorhythm.app.ui.wallet

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.duorhythm.app.R
import com.duorhythm.app.data.model.CardItem
import com.duorhythm.app.databinding.ItemBankCardBinding
import com.duorhythm.app.util.AnimationUtils
import java.util.Locale

class CardsAdapter(
    private var cards: List<CardItem>,
    private var currencySymbol: String = "$",
    private var currencyRate: Double = 1.0,
    private val onCardClick: (CardItem) -> Unit
) : RecyclerView.Adapter<CardsAdapter.CardViewHolder>() {

    fun updateList(
        newCards: List<CardItem>,
        newSymbol: String = currencySymbol,
        newRate: Double = currencyRate
    ) {
        cards = newCards
        currencySymbol = newSymbol
        currencyRate = newRate
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val binding = ItemBankCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CardViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        holder.bind(cards[position])
        AnimationUtils.animateItemEntry(holder.itemView, position)
    }

    override fun getItemCount(): Int = cards.size

    inner class CardViewHolder(private val binding: ItemBankCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(card: CardItem) {
            binding.tvBankName.text = card.cardName.ifEmpty { card.bankName }
            binding.tvCardType.text = card.cardType
            val last4 = card.cardNumber.filter { it.isDigit() }.takeLast(4).ifEmpty { "0000" }
            binding.tvCardNumber.text = "••••  ••••  ••••  $last4"
            binding.tvCardHolder.text = if (card.holderName.isNotBlank()) {
                card.holderName.uppercase(Locale.US)
            } else {
                "CARD HOLDER"
            }

            val convertedBalance = card.balance
            val formattedBalance = if (convertedBalance < 0) {
                String.format(Locale.US, "-%s%.2f", currencySymbol, kotlin.math.abs(convertedBalance))
            } else {
                String.format(Locale.US, "%s%.2f", currencySymbol, convertedBalance)
            }
            binding.tvCardBalance.text = formattedBalance

            // Select background style based on card type / color
            when (card.cardType) {
                "VIRTUAL" -> binding.cardContainer.background =
                    ContextCompat.getDrawable(binding.root.context, R.drawable.bg_bank_card_purple)
                "MASTERCARD" -> binding.cardContainer.background =
                    ContextCompat.getDrawable(binding.root.context, R.drawable.bg_bank_card_emerald)
                else -> binding.cardContainer.background =
                    ContextCompat.getDrawable(binding.root.context, R.drawable.bg_bank_card_navy)
            }

            binding.root.setOnClickListener {
                onCardClick(card)
            }
        }
    }
}

package database.dao

import com.duorhythm.app.data.model.CardItem

interface CardDao {
    fun getCards(profileId: Long): List<CardItem>
    fun insertCard(card: CardItem): Long
    fun updateCardBalance(cardId: Long, newBalance: Double): Int
    fun deleteCard(cardId: Long): Int
}

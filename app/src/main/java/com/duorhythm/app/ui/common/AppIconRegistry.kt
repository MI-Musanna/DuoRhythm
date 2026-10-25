package com.duorhythm.app.ui.common

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.FragmentManager
import com.duorhythm.app.R
import com.google.android.material.card.MaterialCardView
import java.util.Locale

object AppIconRegistry {

    data class IconItem(
        val id: String,
        val title: String,
        val resId: Int,
        val category: String,
        val keywords: String
    )

    val ALL_ICONS: List<IconItem> = listOf(
        IconItem("ic_wallet", "Wallet", R.drawable.ic_wallet, "Finance", "wallet, cash, money, finance, purse, personal"),
        IconItem("ic_cash", "Cash", R.drawable.ic_cash, "Finance", "cash, notes, bills, payment, money, physical"),
        IconItem("ic_bank", "Bank", R.drawable.ic_bank, "Finance", "bank, account, deposit, institution, transfer"),
        IconItem("ic_credit_card", "Credit Card", R.drawable.ic_credit_card, "Finance", "card, credit, debit, visa, mastercard, payment"),
        IconItem("ic_piggy_bank", "Piggy Bank", R.drawable.ic_piggy_bank, "Finance", "piggy, savings, goal, coins, deposit"),
        IconItem("ic_vault", "Vault", R.drawable.ic_vault, "Finance", "vault, safe, security, reserve, lock, emergency"),
        IconItem("ic_currency_exchange", "Currency Exchange", R.drawable.ic_currency_exchange, "Finance", "exchange, forex, currency, convert, bdt, usd"),
        IconItem("ic_analytics", "Analytics", R.drawable.ic_analytics, "Finance", "analytics, chart, stats, metrics, finance, report"),
        IconItem("ic_trending_up", "Trending Up", R.drawable.ic_trending_up, "Finance", "growth, profit, investment, stocks, gain, increase"),
        IconItem("ic_trending_down", "Trending Down", R.drawable.ic_trending_down, "Finance", "loss, decrease, expense, fall, drop"),
        IconItem("ic_receipt", "Receipt", R.drawable.ic_receipt, "Finance", "receipt, bill, invoice, tax, transaction, voucher"),
        IconItem("ic_account_balance", "Account Balance", R.drawable.ic_account_balance, "Finance", "balance, account, treasury, government, court"),
        IconItem("ic_payments", "Payments", R.drawable.ic_payments, "Finance", "payments, salary, transfer, payout, remittance"),
        IconItem("ic_monetization", "Monetization", R.drawable.ic_monetization, "Finance", "money, revenue, earnings, profit, cash, dollar"),
        IconItem("ic_savings", "Savings", R.drawable.ic_savings, "Finance", "savings, fund, interest, wealth, nest egg"),
        IconItem("ic_pie_chart", "Pie Chart", R.drawable.ic_pie_chart, "Finance", "chart, breakdown, budget, distribution, visual"),
        IconItem("ic_bar_chart", "Bar Chart", R.drawable.ic_bar_chart, "Finance", "chart, comparison, bar, data, monthly, cashflow"),
        IconItem("ic_show_chart", "Show Chart", R.drawable.ic_show_chart, "Finance", "chart, line, market, index, tracker, trends"),
        IconItem("ic_shopping_cart", "Shopping Cart", R.drawable.ic_shopping_cart, "Shopping", "shopping, cart, store, buy, supermarket, ecommerce"),
        IconItem("ic_shopping_bag", "Shopping Bag", R.drawable.ic_shopping_bag, "Shopping", "bag, retail, mall, fashion, clothes, purchase"),
        IconItem("ic_store", "Store", R.drawable.ic_store, "Shopping", "store, shop, market, grocery, merchant, local"),
        IconItem("ic_local_mall", "Mall", R.drawable.ic_local_mall, "Shopping", "mall, center, outlet, department, shopping"),
        IconItem("ic_receipt_long", "Long Receipt", R.drawable.ic_receipt_long, "Shopping", "receipt, bill, invoice, utilities, supermarket, statement"),
        IconItem("ic_sell", "Sell", R.drawable.ic_sell, "Shopping", "sell, discount, tag, sale, merchant, offer"),
        IconItem("ic_loyalty", "Loyalty", R.drawable.ic_loyalty, "Shopping", "loyalty, rewards, points, coupon, badge, discount"),
        IconItem("ic_card_giftcard", "Gift Card", R.drawable.ic_card_giftcard, "Shopping", "gift, present, card, reward, voucher, celebration"),
        IconItem("ic_local_offer", "Offer", R.drawable.ic_local_offer, "Shopping", "offer, promo, deal, discount, special, price"),
        IconItem("ic_qr_code", "QR Code", R.drawable.ic_qr_code, "Shopping", "qr, code, scan, contactless, pay, payment"),
        IconItem("ic_shopping_basket", "Basket", R.drawable.ic_shopping_basket, "Shopping", "basket, groceries, pantry, food, market, checkout"),
        IconItem("ic_discount", "Discount", R.drawable.ic_discount, "Shopping", "discount, percentage, sale, coupon, promo, off"),
        IconItem("ic_inventory", "Inventory", R.drawable.ic_inventory, "Shopping", "inventory, stock, storage, supplies, warehouse, items"),
        IconItem("ic_point_of_sale", "POS Terminal", R.drawable.ic_point_of_sale, "Shopping", "pos, terminal, register, checkout, card machine"),
        IconItem("ic_price_check", "Price Check", R.drawable.ic_price_check, "Shopping", "price, check, audit, verify, verify, cost"),
        IconItem("ic_local_atm", "ATM", R.drawable.ic_local_atm, "Shopping", "atm, cash machine, withdraw, dispense, bank"),
        IconItem("ic_school", "School", R.drawable.ic_school, "Academic", "school, university, college, education, campus, degree"),
        IconItem("ic_book", "Book", R.drawable.ic_book, "Academic", "book, textbook, reading, study, notes, literature"),
        IconItem("ic_menu_book", "Open Book", R.drawable.ic_menu_book, "Academic", "book, open, study, reading, course, syllabus"),
        IconItem("ic_auto_stories", "Stories", R.drawable.ic_auto_stories, "Academic", "stories, pages, literature, novel, reading, history"),
        IconItem("ic_library_books", "Library", R.drawable.ic_library_books, "Academic", "library, archive, collection, research, books"),
        IconItem("ic_history_edu", "History", R.drawable.ic_history_edu, "Academic", "history, diploma, certificate, archive, education, degree"),
        IconItem("ic_science", "Science", R.drawable.ic_science, "Academic", "science, chemistry, lab, experiment, flask, research"),
        IconItem("ic_psychology", "Psychology", R.drawable.ic_psychology, "Academic", "psychology, mind, brain, thought, logic, mental"),
        IconItem("ic_architecture", "Architecture", R.drawable.ic_architecture, "Academic", "architecture, compass, drafting, engineering, geometry, math"),
        IconItem("ic_draw", "Draw", R.drawable.ic_draw, "Academic", "draw, pen, pencil, sketch, write, design"),
        IconItem("ic_backpack", "Backpack", R.drawable.ic_backpack, "Academic", "backpack, bag, school, student, commute, supplies"),
        IconItem("ic_assignment", "Assignment", R.drawable.ic_assignment, "Academic", "assignment, homework, project, report, coursework, task"),
        IconItem("ic_graduation", "Graduation", R.drawable.ic_graduation, "Academic", "graduation, cap, degree, alumni, convocation, graduate"),
        IconItem("ic_calculate", "Calculator", R.drawable.ic_calculate, "Academic", "calculate, math, algebra, accounting, formula, stats"),
        IconItem("ic_quiz", "Quiz", R.drawable.ic_quiz, "Academic", "quiz, test, exam, assessment, midterm, question"),
        IconItem("ic_schedule", "Timetable", R.drawable.ic_schedule, "Academic", "schedule, timetable, routine, class, lecture, period"),
        IconItem("ic_smartphone", "Smartphone", R.drawable.ic_smartphone, "Tech", "phone, mobile, smartphone, android, ios, device"),
        IconItem("ic_laptop", "Laptop", R.drawable.ic_laptop, "Tech", "laptop, computer, notebook, coding, work, freelance"),
        IconItem("ic_computer", "Computer", R.drawable.ic_computer, "Tech", "computer, pc, desktop, workstation, monitor, gaming"),
        IconItem("ic_tablet", "Tablet", R.drawable.ic_tablet, "Tech", "tablet, ipad, screen, stylus, digital, drawing"),
        IconItem("ic_headphones", "Headphones", R.drawable.ic_headphones, "Tech", "headphones, audio, sound, music, listen, podcast"),
        IconItem("ic_mouse", "Mouse", R.drawable.ic_mouse, "Tech", "mouse, pointer, click, accessory, peripherals"),
        IconItem("ic_keyboard", "Keyboard", R.drawable.ic_keyboard, "Tech", "keyboard, typing, input, mechanical, keys"),
        IconItem("ic_memory", "Memory & RAM", R.drawable.ic_memory, "Tech", "memory, ram, hardware, chip, cpu, processor"),
        IconItem("ic_devices", "Devices", R.drawable.ic_devices, "Tech", "devices, electronics, hardware, multiplatform, sync"),
        IconItem("ic_router", "Router", R.drawable.ic_router, "Tech", "router, modem, network, internet, broadband, connection"),
        IconItem("ic_wifi", "Wi-Fi", R.drawable.ic_wifi, "Tech", "wifi, wireless, network, internet, data, hotspot"),
        IconItem("ic_terminal", "Terminal", R.drawable.ic_terminal, "Tech", "terminal, console, shell, bash, cli, command"),
        IconItem("ic_code", "Code", R.drawable.ic_code, "Tech", "code, developer, programming, software, html, kotlin"),
        IconItem("ic_bug_report", "Bug Report", R.drawable.ic_bug_report, "Tech", "bug, debug, error, issue, fix, report"),
        IconItem("ic_videogame_asset", "Gamepad", R.drawable.ic_videogame_asset, "Tech", "gamepad, controller, gaming, console, video games"),
        IconItem("ic_smart_toy", "AI & Robot", R.drawable.ic_smart_toy, "Tech", "robot, bot, ai, smart, algorithm, automation"),
        IconItem("ic_directions_car", "Car", R.drawable.ic_directions_car, "Transport", "car, automobile, drive, ride, vehicle, parking"),
        IconItem("ic_directions_bus", "Bus", R.drawable.ic_directions_bus, "Transport", "bus, transit, public, coach, shuttle, commute"),
        IconItem("ic_two_wheeler", "Motorcycle", R.drawable.ic_two_wheeler, "Transport", "motorcycle, bike, scooter, ride, fuel, delivery"),
        IconItem("ic_pedal_bike", "Bicycle", R.drawable.ic_pedal_bike, "Transport", "bicycle, cycle, eco, exercise, commute, pedal"),
        IconItem("ic_flight", "Flight", R.drawable.ic_flight, "Transport", "flight, plane, airport, travel, vacation, airline"),
        IconItem("ic_train", "Train", R.drawable.ic_train, "Transport", "train, railway, metro, journey, travel, railway"),
        IconItem("ic_subway", "Subway", R.drawable.ic_subway, "Transport", "subway, underground, metro, tube, station, commute"),
        IconItem("ic_local_taxi", "Taxi", R.drawable.ic_local_taxi, "Transport", "taxi, cab, uber, fare, ride, pickup"),
        IconItem("ic_local_gas_station", "Fuel & Gas", R.drawable.ic_local_gas_station, "Transport", "gas, fuel, petrol, diesel, octane, refuel"),
        IconItem("ic_ev_station", "EV Charging", R.drawable.ic_ev_station, "Transport", "ev, electric, charge, station, battery, green"),
        IconItem("ic_commute", "Commute", R.drawable.ic_commute, "Transport", "commute, journey, transit, travel, road, distance"),
        IconItem("ic_navigation", "Navigation", R.drawable.ic_navigation, "Transport", "navigation, gps, directions, route, compass, location"),
        IconItem("ic_map", "Map", R.drawable.ic_map, "Transport", "map, geography, tour, destination, plan, guide"),
        IconItem("ic_hotel", "Hotel", R.drawable.ic_hotel, "Transport", "hotel, stay, accommodation, motel, vacation, resort"),
        IconItem("ic_luggage", "Luggage", R.drawable.ic_luggage, "Transport", "luggage, baggage, suitcase, travel, trip, flight"),
        IconItem("ic_local_shipping", "Shipping", R.drawable.ic_local_shipping, "Transport", "shipping, parcel, courier, delivery, package, cargo"),
        IconItem("ic_restaurant", "Restaurant", R.drawable.ic_restaurant, "Food", "restaurant, dine, meal, dinner, food, dining"),
        IconItem("ic_fastfood", "Fast Food", R.drawable.ic_fastfood, "Food", "burger, fries, fastfood, snack, lunch, takeaway"),
        IconItem("ic_local_cafe", "Coffee & Cafe", R.drawable.ic_local_cafe, "Food", "coffee, tea, cafe, brew, espresso, morning"),
        IconItem("ic_local_bar", "Bar & Drinks", R.drawable.ic_local_bar, "Food", "bar, drink, beverage, cocktail, club, social"),
        IconItem("ic_local_pizza", "Pizza", R.drawable.ic_local_pizza, "Food", "pizza, slice, italian, fastfood, delivery, cheese"),
        IconItem("ic_ramen_dining", "Noodles & Soup", R.drawable.ic_ramen_dining, "Food", "ramen, noodles, soup, bowl, asian, lunch"),
        IconItem("ic_lunch_dining", "Lunch Box", R.drawable.ic_lunch_dining, "Food", "lunch, meal, sandwich, office, tiffin, afternoon"),
        IconItem("ic_dinner_dining", "Fine Dining", R.drawable.ic_dinner_dining, "Food", "dinner, plate, gourmet, date, evening, dining"),
        IconItem("ic_bakery_dining", "Bakery", R.drawable.ic_bakery_dining, "Food", "bakery, bread, pastry, cake, croissant, sweet"),
        IconItem("ic_icecream", "Ice Cream", R.drawable.ic_icecream, "Food", "icecream, dessert, gelato, sweet, cone, treat"),
        IconItem("ic_local_grocery_store", "Grocery", R.drawable.ic_local_grocery_store, "Food", "grocery, provisions, vegetables, fruit, cooking, market"),
        IconItem("ic_emoji_food_beverage", "Tea & Drinks", R.drawable.ic_emoji_food_beverage, "Food", "tea, cup, hot, drink, herbal, beverage"),
        IconItem("ic_takeout_dining", "Takeout", R.drawable.ic_takeout_dining, "Food", "takeout, delivery, package, box, order, food"),
        IconItem("ic_liquor", "Liquor", R.drawable.ic_liquor, "Food", "liquor, bottle, wine, spirits, beverage, celebration"),
        IconItem("ic_set_meal", "Set Meal", R.drawable.ic_set_meal, "Food", "set meal, combo, feast, plate, dishes, banquet"),
        IconItem("ic_kitchen", "Kitchen", R.drawable.ic_kitchen, "Food", "kitchen, fridge, pantry, cook, cooking, chef"),
        IconItem("ic_fitness_center", "Gym & Fitness", R.drawable.ic_fitness_center, "Health", "gym, workout, fitness, weights, exercise, training"),
        IconItem("ic_directions_run", "Running", R.drawable.ic_directions_run, "Health", "running, cardio, jogging, fitness, athlete, track"),
        IconItem("ic_favorite", "Health & Heart", R.drawable.ic_favorite, "Health", "health, heart, wellness, love, care, life"),
        IconItem("ic_medical_services", "Medical Services", R.drawable.ic_medical_services, "Health", "doctor, clinic, medical, prescription, care, health"),
        IconItem("ic_local_hospital", "Hospital", R.drawable.ic_local_hospital, "Health", "hospital, emergency, clinic, treatment, surgery, aid"),
        IconItem("ic_medication", "Pharmacy", R.drawable.ic_medication, "Health", "pharmacy, medicine, pills, drug, prescription, capsule"),
        IconItem("ic_sports_soccer", "Soccer", R.drawable.ic_sports_soccer, "Health", "soccer, football, sport, ball, match, tournament"),
        IconItem("ic_pool", "Swimming", R.drawable.ic_pool, "Health", "swimming, pool, water, swim, laps, training"),
        IconItem("ic_self_improvement", "Meditation", R.drawable.ic_self_improvement, "Health", "meditation, yoga, mindfulness, calm, breath, peace"),
        IconItem("ic_spa", "Spa & Wellness", R.drawable.ic_spa, "Health", "spa, massage, wellness, relax, skincare, therapy"),
        IconItem("ic_healing", "First Aid", R.drawable.ic_healing, "Health", "bandage, healing, first aid, recovery, plaster, clinic"),
        IconItem("ic_water_drop", "Water & Hydration", R.drawable.ic_water_drop, "Health", "water, hydration, drink, aqua, thirst, purity"),
        IconItem("ic_monitor_heart", "Heart Monitor", R.drawable.ic_monitor_heart, "Health", "heart, pulse, monitor, ecg, vitals, cardio"),
        IconItem("ic_sports_tennis", "Tennis", R.drawable.ic_sports_tennis, "Health", "tennis, racket, court, sport, match, recreation"),
        IconItem("ic_sports_gymnastics", "Gymnastics", R.drawable.ic_sports_gymnastics, "Health", "gymnastics, flexibility, agility, dance, acrobatic"),
        IconItem("ic_emergency", "Emergency", R.drawable.ic_emergency, "Health", "emergency, urgent, red cross, siren, alert, ambulance"),
        IconItem("ic_movie", "Movie & Cinema", R.drawable.ic_movie, "Entertainment", "movie, film, cinema, theatre, show, blockbuster"),
        IconItem("ic_theater_comedy", "Comedy & Drama", R.drawable.ic_theater_comedy, "Entertainment", "comedy, drama, theater, stage, performance, masks"),
        IconItem("ic_music", "Music", R.drawable.ic_music, "Entertainment", "music, song, audio, track, album, player"),
        IconItem("ic_music_note", "Music Note", R.drawable.ic_music_note, "Entertainment", "note, tune, harmony, melody, acoustic, score"),
        IconItem("ic_sports_esports", "Esports", R.drawable.ic_sports_esports, "Entertainment", "esports, stream, gaming, competition, league, gamer"),
        IconItem("ic_mic", "Mic & Singing", R.drawable.ic_mic, "Entertainment", "mic, microphone, singing, karaoke, podcast, speech"),
        IconItem("ic_headset", "Headset", R.drawable.ic_headset, "Entertainment", "headset, audio, communication, support, calls"),
        IconItem("ic_brush", "Painting & Art", R.drawable.ic_brush, "Entertainment", "brush, paint, art, artist, canvas, draw"),
        IconItem("ic_palette", "Color Palette", R.drawable.ic_palette, "Entertainment", "palette, color, craft, hobby, design, art"),
        IconItem("ic_sports_basketball", "Basketball", R.drawable.ic_sports_basketball, "Entertainment", "basketball, hoops, court, sport, nba, slam"),
        IconItem("ic_park", "Nature & Park", R.drawable.ic_park, "Entertainment", "park, tree, nature, outdoor, hiking, garden"),
        IconItem("ic_celebration", "Celebration", R.drawable.ic_celebration, "Entertainment", "celebration, party, confetti, birthday, festival, event"),
        IconItem("ic_piano", "Piano", R.drawable.ic_piano, "Entertainment", "piano, keyboard, classical, instrument, music, keys"),
        IconItem("ic_casino", "Games & Dice", R.drawable.ic_casino, "Entertainment", "casino, dice, game, bet, leisure, cards"),
        IconItem("ic_photo", "Photography", R.drawable.ic_photo, "Entertainment", "photo, gallery, picture, memory, album, snapshot"),
        IconItem("ic_camera", "Camera", R.drawable.ic_camera, "Entertainment", "camera, photography, shoot, lens, shutter, video"),
        IconItem("ic_live_tv", "Live TV", R.drawable.ic_live_tv, "Entertainment", "tv, television, streaming, broadcast, series, episodes"),
        IconItem("ic_emoji_events", "Trophy & Award", R.drawable.ic_emoji_events, "Entertainment", "trophy, cup, award, winner, prize, achievement"),
    )

    fun getAllIcons(): List<IconItem> = ALL_ICONS

    fun getCategories(): List<String> = listOf(
        "All",
        "Finance",
        "Shopping",
        "Academic",
        "Tech",
        "Transport",
        "Food",
        "Health",
        "Entertainment"
    )

    fun getQuickPickIcons(): List<IconItem> = listOf(
        ALL_ICONS.first { it.id == "ic_wallet" },
        ALL_ICONS.first { it.id == "ic_cash" },
        ALL_ICONS.first { it.id == "ic_bank" },
        ALL_ICONS.first { it.id == "ic_credit_card" },
        ALL_ICONS.first { it.id == "ic_piggy_bank" }
    )

    fun isIconMatch(idA: String, idB: String): Boolean {
        val cleanA = idA.trim().lowercase(Locale.US)
        val cleanB = idB.trim().lowercase(Locale.US)
        if (cleanA == cleanB) return true
        if (cleanA.removePrefix("ic_") == cleanB.removePrefix("ic_")) return true
        val normA = normalizeIconAlias(cleanA)
        val normB = normalizeIconAlias(cleanB)
        return normA == normB
    }

    private fun normalizeIconAlias(id: String): String {
        val clean = id.removePrefix("ic_").replace("_", "")
        return when (clean) {
            "creditcard", "card" -> "card"
            "piggybank", "piggy", "savings" -> "piggybank"
            "vault", "safe", "shield" -> "vault"
            else -> clean
        }
    }

    fun setupQuickPickIconsRow(
        context: Context,
        container: LinearLayout,
        initialIcon: String,
        fragmentManager: FragmentManager? = null,
        initialAccentColor: String = "#06B6D4",
        onIconSelected: (String) -> Unit
    ): (newIcon: String, newAccentColor: String) -> Unit {
        container.removeAllViews()

        var activeIcon = initialIcon
        var activeAccentColor = initialAccentColor

        val dp = { value: Int -> (value * context.resources.displayMetrics.density).toInt() }
        val quickPickItems = getQuickPickIcons()
        val quickPickViews = mutableListOf<Pair<IconItem, Pair<MaterialCardView, ImageView>>>()

        // 1. Create quick-pick cards (5 items)
        for (item in quickPickItems) {
            val card = MaterialCardView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                    marginEnd = dp(8)
                }
                radius = dp(14).toFloat()
                cardElevation = 0f
                strokeWidth = dp(1)
                isClickable = true
                isFocusable = true
            }

            val iv = ImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(dp(24), dp(24)).apply {
                    gravity = Gravity.CENTER
                }
                setImageResource(item.resId)
            }

            card.addView(iv)

            card.setOnClickListener {
                activeIcon = item.id
                // Use the returned lambda to refresh
                onIconSelected(activeIcon)
            }

            container.addView(card)
            quickPickViews.add(Pair(item, Pair(card, iv)))
        }

        // 2. Create "+ More Icons" card with ic_apps
        val moreCard = MaterialCardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                marginEnd = dp(8)
            }
            radius = dp(14).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            isClickable = true
            isFocusable = true
            contentDescription = "More Icons"
        }

        val moreIv = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(dp(24), dp(24)).apply {
                gravity = Gravity.CENTER
            }
            setImageResource(R.drawable.ic_apps)
        }

        moreCard.addView(moreIv)

        fun refreshUi() {
            val parsedColor = try {
                Color.parseColor(activeAccentColor)
            } catch (_: Exception) {
                Color.parseColor("#06B6D4")
            }
            val glowColor = ColorUtils.setAlphaComponent(parsedColor, (255 * 0.18).toInt())

            for ((item, views) in quickPickViews) {
                val (card, iv) = views
                val isSelected = isIconMatch(item.id, activeIcon)
                if (isSelected) {
                    card.setCardBackgroundColor(glowColor)
                    card.strokeColor = parsedColor
                    card.strokeWidth = dp(2)
                    iv.setColorFilter(parsedColor)
                } else {
                    card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.surface_variant))
                    card.strokeColor = ContextCompat.getColor(context, R.color.card_stroke)
                    card.strokeWidth = dp(1)
                    iv.setColorFilter(ContextCompat.getColor(context, R.color.text_secondary))
                }
            }

            val isQuickPick = quickPickItems.any { isIconMatch(it.id, activeIcon) }
            if (!isQuickPick) {
                moreCard.setCardBackgroundColor(glowColor)
                moreCard.strokeColor = parsedColor
                moreCard.strokeWidth = dp(2)
                moreIv.setImageResource(getIconDrawable(activeIcon))
                moreIv.setColorFilter(parsedColor)
            } else {
                moreCard.setCardBackgroundColor(ContextCompat.getColor(context, R.color.surface_variant))
                moreCard.strokeColor = ContextCompat.getColor(context, R.color.card_stroke)
                moreCard.strokeWidth = dp(1)
                moreIv.setImageResource(R.drawable.ic_apps)
                moreIv.setColorFilter(ContextCompat.getColor(context, R.color.text_secondary))
            }
        }

        // Attach click to quick picks
        for ((item, views) in quickPickViews) {
            val (card, _) = views
            card.setOnClickListener {
                activeIcon = item.id
                refreshUi()
                onIconSelected(activeIcon)
            }
        }

        moreCard.setOnClickListener {
            val fm = fragmentManager ?: (context as? androidx.fragment.app.FragmentActivity)?.supportFragmentManager
            if (fm != null) {
                IconPickerBottomSheet.show(fm, activeIcon) { chosenId ->
                    activeIcon = chosenId
                    refreshUi()
                    onIconSelected(activeIcon)
                }
            }
        }

        container.addView(moreCard)
        refreshUi()

        return { newIcon, newColor ->
            activeIcon = newIcon
            activeAccentColor = newColor
            refreshUi()
        }
    }

    fun getIconDrawable(iconId: String?): Int {
        if (iconId.isNullOrBlank()) return R.drawable.ic_wallet
        val clean = iconId.trim().lowercase(Locale.US)

        val directMatch = ALL_ICONS.firstOrNull {
            it.id.equals(clean, ignoreCase = true) ||
            it.id.removePrefix("ic_").equals(clean.removePrefix("ic_"), ignoreCase = true)
        }
        if (directMatch != null) return directMatch.resId

        return when (clean) {
            "cash" -> R.drawable.ic_cash
            "wallet" -> R.drawable.ic_wallet
            "bank" -> R.drawable.ic_bank
            "card", "credit_card" -> R.drawable.ic_credit_card
            "piggybank", "piggy", "savings" -> R.drawable.ic_piggy_bank
            "vault", "safe", "shield" -> R.drawable.ic_vault
            "phone", "mfs", "mobile", "bkash", "nagad" -> R.drawable.ic_smartphone
            "crypto", "coin", "bolt" -> R.drawable.ic_bolt
            "target", "goal" -> R.drawable.ic_target
            "school", "study" -> R.drawable.ic_school
            "tools", "work" -> R.drawable.ic_tools
            "analytics" -> R.drawable.ic_analytics
            "subscription" -> R.drawable.ic_subscription
            "calendar" -> R.drawable.ic_calendar
            "person" -> R.drawable.ic_person
            "settings" -> R.drawable.ic_settings
            "tasks" -> R.drawable.ic_tasks
            else -> R.drawable.ic_wallet
        }
    }
}

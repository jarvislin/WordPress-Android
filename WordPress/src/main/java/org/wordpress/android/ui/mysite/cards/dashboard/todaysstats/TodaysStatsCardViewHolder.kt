package org.wordpress.android.ui.mysite.cards.dashboard.todaysstats

import android.view.ViewGroup
import org.wordpress.android.databinding.MySiteTodaysStatsCardBinding
import org.wordpress.android.ui.mysite.MySiteCardAndItem.Card.DashboardCards.DashboardCard.TodaysStatsCard.TodaysStatsCardWithData
import org.wordpress.android.ui.mysite.cards.dashboard.CardViewHolder
import org.wordpress.android.ui.utils.UiHelpers
import org.wordpress.android.util.extensions.viewBinding

class TodaysStatsCardViewHolder(
    parent: ViewGroup,
    private val uiHelpers: UiHelpers
) : CardViewHolder<MySiteTodaysStatsCardBinding>(
        parent.viewBinding(MySiteTodaysStatsCardBinding::inflate)
) {
    fun bind(card: TodaysStatsCardWithData) = with(binding) {
        uiHelpers.setTextOrHide(viewsCount, card.views)
        uiHelpers.setTextOrHide(visitorsCount, card.visitors)
        uiHelpers.setTextOrHide(likesCount, card.likes)
        uiHelpers.setTextOrHide(footerLink.linkLabel, card.footerLink.label)
        footerLink.linkLabel.setOnClickListener {
            card.footerLink.onClick.invoke()
        }
        mySiteTodaysStatCard.setOnClickListener {
            card.onCardClick.invoke()
        }
    }
}

package org.wordpress.android.ui.mysite

import androidx.recyclerview.widget.DiffUtil
import org.apache.commons.lang3.NotImplementedException
import org.wordpress.android.ui.mysite.MySiteCardAndItem.Card.DashboardCards
import org.wordpress.android.ui.mysite.MySiteCardAndItem.Card.DomainRegistrationCard
import org.wordpress.android.ui.mysite.MySiteCardAndItem.Card.QuickActionsCard
import org.wordpress.android.ui.mysite.MySiteCardAndItem.Card.QuickStartCard
import org.wordpress.android.ui.mysite.MySiteCardAndItem.DynamicCard.QuickStartDynamicCard
import org.wordpress.android.ui.mysite.MySiteCardAndItem.Item.CategoryHeaderItem
import org.wordpress.android.ui.mysite.MySiteCardAndItem.Item.InfoItem
import org.wordpress.android.ui.mysite.MySiteCardAndItem.Item.ListItem

class MySiteAdapterDiffCallback(
    private val oldCardAndItems: List<MySiteCardAndItem>,
    private val updatedCardAndItems: List<MySiteCardAndItem>
) : DiffUtil.Callback() {
    override fun getOldListSize(): Int = oldCardAndItems.size

    override fun getNewListSize(): Int = updatedCardAndItems.size

    @Suppress("ComplexMethod")
    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldItem = oldCardAndItems[oldItemPosition]
        val updatedItem = updatedCardAndItems[newItemPosition]
        return oldItem.type == updatedItem.type && when {
            oldItem is QuickActionsCard && updatedItem is QuickActionsCard -> true
            oldItem is DomainRegistrationCard && updatedItem is DomainRegistrationCard -> true
            oldItem is QuickStartCard && updatedItem is QuickStartCard -> true
            oldItem is QuickStartDynamicCard && updatedItem is QuickStartDynamicCard -> oldItem.id == updatedItem.id
            oldItem is InfoItem && updatedItem is InfoItem -> oldItem.title == updatedItem.title
            oldItem is CategoryHeaderItem && updatedItem is CategoryHeaderItem -> oldItem.title == updatedItem.title
            oldItem is ListItem && updatedItem is ListItem -> oldItem.primaryText == updatedItem.primaryText
            oldItem is DashboardCards && updatedItem is DashboardCards -> true
            else -> throw NotImplementedException("Diff not implemented yet")
        }
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
            oldCardAndItems[oldItemPosition] == updatedCardAndItems[newItemPosition]
}

package org.wordpress.android.ui.accounts

import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import org.wordpress.android.WordPress
import org.wordpress.android.databinding.PostSignupInterstitialActivityBinding
import org.wordpress.android.ui.ActivityLauncher
import org.wordpress.android.ui.LocaleAwareActivity
import org.wordpress.android.viewmodel.accounts.PostSignupInterstitialViewModel
import org.wordpress.android.viewmodel.accounts.PostSignupInterstitialViewModel.NavigationAction
import org.wordpress.android.viewmodel.accounts.PostSignupInterstitialViewModel.NavigationAction.DISMISS
import org.wordpress.android.viewmodel.accounts.PostSignupInterstitialViewModel.NavigationAction.START_SITE_CONNECTION_FLOW
import org.wordpress.android.viewmodel.accounts.PostSignupInterstitialViewModel.NavigationAction.START_SITE_CREATION_FLOW
import javax.inject.Inject

class PostSignupInterstitialActivity : LocaleAwareActivity() {
    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var viewModel: PostSignupInterstitialViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (application as WordPress).component().inject(this)

        LoginFlowThemeHelper.injectMissingCustomAttributes(theme)

        viewModel = ViewModelProvider(this, viewModelFactory)
                .get(PostSignupInterstitialViewModel::class.java)
        val binding = PostSignupInterstitialActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        with(binding.postSignupInterstitial) {

            viewModel.onInterstitialShown()

            createNewSiteButton.setOnClickListener { viewModel.onCreateNewSiteButtonPressed() }
            addSelfHostedSiteButton.setOnClickListener { viewModel.onAddSelfHostedSiteButtonPressed() }
            dismissButton.setOnClickListener { viewModel.onDismissButtonPressed() }
        }

        viewModel.navigationAction.observe(this, { executeAction(it) })
    }

    override fun onBackPressed() {
        viewModel.onBackButtonPressed()
    }

    private fun executeAction(navigationAction: NavigationAction) = when (navigationAction) {
        START_SITE_CREATION_FLOW -> startSiteCreationFlow()
        START_SITE_CONNECTION_FLOW -> startSiteConnectionFlow()
        DISMISS -> dismiss()
    }

    private fun startSiteCreationFlow() {
        ActivityLauncher.showMainActivityAndSiteCreationActivity(this)
        finish()
    }

    private fun startSiteConnectionFlow() {
        ActivityLauncher.addSelfHostedSiteForResult(this)
        finish()
    }

    private fun dismiss() {
        ActivityLauncher.viewReader(this)
        finish()
    }
}

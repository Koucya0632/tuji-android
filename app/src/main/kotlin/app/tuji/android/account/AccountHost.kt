package app.tuji.android.account

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuji.android.AccountRoute
import app.tuji.android.AtlasRoute
import app.tuji.android.BuildConfig
import app.tuji.android.ShellContext
import app.tuji.android.core.catalog.CategoryShelf
import app.tuji.android.core.study.AppMutation
import app.tuji.android.core.study.MasteryDistribution
import app.tuji.android.credits.CreditWalletCard
import app.tuji.android.membership.CreditPackButtons
import app.tuji.android.membership.MembershipOffer
import app.tuji.android.membership.MembershipPurchase
import app.tuji.android.membership.MembershipScreen
import app.tuji.android.profile.BlockedAuthorsScreen
import app.tuji.android.profile.EditProfileScreen
import app.tuji.android.profile.EditProfileViewModel
import app.tuji.android.rememberDeviceLanguage
import app.tuji.android.settings.FeedbackSender
import app.tuji.android.settings.ReminderSettingsSection
import app.tuji.android.settings.SettingsBusy
import app.tuji.android.settings.SettingsReadiness
import app.tuji.android.settings.SettingsScreen
import app.tuji.android.settings.StudyThemesScreen
import app.tuji.android.settings.shareApp
import kotlinx.coroutines.launch

/** 我's pages: 設定, 編輯個人資料, 已封鎖的人, 學習主題, 會員方案 and 學習統計. */
@Composable
fun AccountHost(route: AccountRoute, shell: ShellContext) {
    val app = shell.app
    val page = shell.from(route)
    val settings = shell.settings
    val learning = shell.learning
    val deviceLanguage = rememberDeviceLanguage()
    val settingsLoaded by app.settingsStore.loaded.collectAsStateWithLifecycle()
    val settingsLoadFailed by app.settingsStore.loadFailed.collectAsStateWithLifecycle()
    val readiness = SettingsReadiness.of(settingsLoaded, settingsLoadFailed)
    val retryLoad: () -> Unit = { shell.scope.launch { app.settingsStore.load(deviceLanguage) } }

    when (route) {
        AccountRoute.Settings -> {
            var busy by remember { mutableStateOf(SettingsBusy()) }
            val context = LocalContext.current
            // 意見收集 sends the same version string 報錯 does.
            val feedback = remember(shell.uiLang) {
                FeedbackSender(
                    appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    uiLang = shell.uiLang,
                    submit = app.study::submitFeedback,
                )
            }
            SettingsScreen(
                readiness = readiness,
                onRetryLoad = retryLoad,
                settings = settings,
                busy = busy,
                bottomPadding = 0.dp,
                onChange = { change -> app.settingsStore.update(change) },
                onClearProgress = {
                    shell.scope.launch {
                        busy = busy.copy(clearing = true)
                        runCatching { app.study.clearProgress() }
                        busy = busy.copy(clearing = false)
                        // Everything this screen just erased is drawn somewhere
                        // else, and none of those stores can know it happened.
                        app.invalidator.post(AppMutation.ProgressCleared)
                        page.pop()
                    }
                },
                onDeleteAccount = {
                    shell.scope.launch {
                        busy = busy.copy(deleting = true)
                        runCatching { app.study.deleteAccount() }
                        busy = busy.copy(deleting = false)
                        // Signing out is what takes the user off this screen;
                        // the account it belonged to is gone.
                        app.auth.signOut()
                    }
                },
                onSignOut = { shell.scope.launch { app.auth.signOut() } },
                onOpenStudyThemes = { page.push(AccountRoute.StudyThemes) },
                onEditProfile = { page.push(AccountRoute.EditProfile) },
                onOpenBlocked = { page.push(AccountRoute.BlockedAuthors) },
                onOpenMembership = shell::openMembership,
                tier = shell.tier,
                offer = MembershipOffer.from(shell.entitlement),
                feedback = feedback,
                onShareApp = {
                    app.analytics.shareApp()
                    shareApp(context)
                },
                reminders = { ReminderSettingsSection(app.studyReminders) },
            )
        }

        AccountRoute.EditProfile -> {
            val vm = remember {
                EditProfileViewModel(
                    accounts = app.atlas,
                    profiles = app.atlas,
                    onSaved = { author ->
                        // The name and face other people see changed: the
                        // session's greeting, 我's row and 物見's own-page row
                        // all draw it.
                        app.auth.applyProfile(
                            nickname = author.displayName?.takeIf { it != author.handle },
                            avatar = author.avatar,
                        )
                        app.invalidator.post(AppMutation.ProfileSaved)
                        page.pop()
                    },
                ).also { it.load() }
            }
            val profileState by vm.state.collectAsStateWithLifecycle()
            EditProfileScreen(
                state = profileState,
                onBack = page::pop,
                onSave = vm::save,
                onNickname = vm::setNickname,
                onBio = vm::setBio,
                onImage = vm::stageImage,
                onUseDefaultAvatar = vm::useDefaultAvatar,
            )
        }

        AccountRoute.BlockedAuthors -> {
            val community = shell.models.community
            val blocked by community.blocked.collectAsStateWithLifecycle()
            var working by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(Unit) { community.loadBlocksIfNeeded() }
            BlockedAuthorsScreen(
                handles = blocked.sorted,
                working = working,
                onUnblock = { handle ->
                    working = handle
                    community.unblock(handle) { working = null }
                },
            )
        }

        AccountRoute.StudyThemes -> StudyThemesScreen(
            readiness = readiness,
            onRetryLoad = retryLoad,
            selected = settings.studyCategories,
            categories = learning.catalog.categories,
            presets = learning.catalog.presets,
            uiLang = shell.uiLang,
            onChange = { picked -> app.settingsStore.update { it.copy(studyCategories = picked) } },
            studyable = shell.studyable,
            onLocked = shell::openMembership,
            // Locked themes too: 圖鑑 is browsable without membership.
            onOpenTheme = { category ->
                page.push(AtlasRoute.Shelf(category.id, CategoryShelf.title(category, shell.uiLang)))
            },
        )

        AccountRoute.Membership -> MembershipPage(shell)

        AccountRoute.ProgressStats -> ProgressStatsScreen(
            completion = learning.completion,
            progress = learning.progress,
            spread = remember(learning.scores) { MasteryDistribution.of(learning.scores.byId) },
            masteryLoaded = learning.scores.loaded,
            categories = learning.catalog.categories,
            studyCategories = settings.studyCategories,
            onBack = page::pop,
        )
    }
}

/** 會員方案: Play's offers, the wallet for a points account, and 首購禮. */
@Composable
private fun MembershipPage(shell: ShellContext) {
    val app = shell.app
    val scope = rememberCoroutineScope()
    val owner = shell.ownerId
    val activity = LocalActivity.current
    val billing by app.playBilling.state.collectAsStateWithLifecycle()
    val checkIn by app.checkInStore.snapshot.collectAsStateWithLifecycle()
    LaunchedEffect(owner) {
        if (owner != null) {
            app.playBilling.load()
            app.playBilling.reconcile()
        }
    }
    DisposableEffect(Unit) { onDispose { app.playBilling.dismissNotice() } }
    val purchase = if (owner != null && activity != null) {
        MembershipPurchase(
            state = billing,
            onBuy = { app.playBilling.purchase(activity, it) },
            onReload = { scope.launch { app.playBilling.load() } },
            onRestore = {
                scope.launch {
                    app.playBilling.reconcile(quiet = false)
                    app.playBilling.load()
                }
            },
        )
    } else {
        null
    }
    MembershipScreen(
        offer = MembershipOffer.from(shell.entitlement),
        walletCard = if (shell.entitlement?.billingMode == "credits" && owner != null) {
            {
                CreditWalletCard(
                    app.api, app.atlas, owner, shell::currentOwner,
                    refreshKey = billing.deliveries,
                    packs = purchase?.let { p -> { CreditPackButtons(p) } },
                )
            }
        } else {
            null
        },
        purchase = purchase,
        firstPurchaseGift = checkIn.catalog?.policy?.let { policy ->
            val gift = policy.firstPurchaseGift ?: 0
            policy.monthlyGift?.takeIf { gift > 0 }?.let { gift to it }
        },
    )
}

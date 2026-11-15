package app.tuji.android.manage

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuji.android.AccountRoute
import app.tuji.android.ManageRoute
import app.tuji.android.R
import app.tuji.android.ShellContext
import app.tuji.android.TabShell
import app.tuji.android.core.community.DeleteWarning
import app.tuji.android.core.community.ImageStatus
import app.tuji.android.core.design.TujiIndeterminateBar
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.model.AtlasMyCollection
import app.tuji.android.core.model.CaptureEntry
import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.SpotTagging
import app.tuji.android.credits.CreditCaptureModel
import app.tuji.android.credits.CreditCaptureScreen
import app.tuji.android.travel.TravelLoad

/**
 * 自製圖鑑: 拍照, 圖鑑管理, one card's page and 編輯合集.
 *
 * [onCaptureQueued] is the shell's: a capture handed to 生成佇列 closes onto
 * 圖鑑's 我做的, and which shelf 圖鑑 opens on is the tab's state, not a page's.
 */
@Composable
fun ManageHost(route: ManageRoute, shell: ShellContext, onCaptureQueued: () -> Unit) {
    val app = shell.app
    val page = shell.from(route)
    val manage = shell.models.manage
    val myCollections = shell.models.myCollections
    val manageState by manage.state.collectAsStateWithLifecycle()
    val collectionsState by myCollections.state.collectAsStateWithLifecycle()
    val captureJobs by app.captureQueue.jobs.collectAsStateWithLifecycle()

    when (route) {
        ManageRoute.Home -> {
            LaunchedEffect(Unit) { manage.load() }
            // The slot count in the header: on arrival, and again whenever a
            // delete changes how many cards there are.
            LaunchedEffect(manageState.rows.size) { shell.models.account.refreshEntitlement() }
            AtlasManageScreen(
                state = manageState,
                uiLanguage = shell.uiLanguage,
                onBack = page::pop,
                onRetry = manage::load,
                onSelecting = manage::setSelecting,
                onToggle = manage::toggle,
                onOpen = { page.push(ManageRoute.Card(it)) },
                onDelete = { ids -> manage.delete(ids) },
                collections = collectionsState,
                onLoadCollections = myCollections::load,
                onRefreshCollections = { myCollections.reload() },
                onCreateCollection = { title, description, onCreated -> myCollections.create(title, description) { onCreated() } },
                onDismissCreateError = myCollections::dismissCreateError,
                onOpenCollection = { page.push(ManageRoute.CollectionEdit(it)) },
                slots = shell.entitlement?.let { it.usage.atlasSlots to it.atlasSlotsLimit },
                onSwitchDirection = {
                    val other = LearningDirection.entries.first { it != shell.direction }
                    app.settingsStore.update { it.copy(learningDirection = other.wire) }
                },
                inFlight = remember(captureJobs) { captureJobs.associate { it.imageId to it.progress } },
            )
        }

        is ManageRoute.CollectionEdit -> {
            val vm = remember(route.collectionId) {
                CollectionEditViewModel(
                    collectionId = route.collectionId,
                    authoring = app.atlas,
                    onMutation = app.invalidator::post,
                    onNeedsMembership = shell::openMembership,
                ).also { it.load() }
            }
            val editState by vm.state.collectAsStateWithLifecycle()
            CollectionEditScreen(
                state = editState,
                deleteWarning = DeleteWarning.of(editState.review),
                deleting = collectionsState.deleting,
                onBack = page::pop,
                onRetry = vm::load,
                onTitle = vm::setTitle,
                onDescription = vm::setDescription,
                onSaveMeta = { vm.saveMeta() },
                onSaveThenLeave = { vm.saveMeta { page.pop() } },
                onAvatar = vm::uploadAvatar,
                onOpenPicker = vm::loadCandidates,
                onAdd = vm::addMember,
                onRemove = vm::removeMember,
                onSubmit = vm::submit,
                onWithdraw = vm::withdraw,
                onDelete = {
                    myCollections.delete(
                        AtlasMyCollection(id = route.collectionId, reviewStatus = editState.collection?.reviewStatus),
                    ) { page.pop() }
                },
            )
        }

        is ManageRoute.Card -> {
            // A card's page offers the 景點 the map names, wherever it is opened from.
            TravelLoad(shell)
            val travel by app.travelStore.state.collectAsStateWithLifecycle()
            val row = manageState.row(route.imageId)
            // 待確認 alone does not say the result can still be picked from: a
            // photo left by the retired pre-credit flow reads the same and has
            // no run to reopen.
            val waiting = row != null && row.item == null && row.imageStatus == ImageStatus.NeedsReview
            val resumable by produceState(false, route.imageId, waiting) {
                value = waiting && runCatching { app.credits.history() }.getOrNull()?.let {
                    CreditCaptureModel.resumable(route.imageId, it, app.captureQueue.creditOperationIds)
                } != null
            }
            ManageCardScreen(
                row = row,
                uiLanguage = shell.uiLanguage,
                withdrawing = manageState.withdrawing,
                publishing = manageState.publishing,
                actionFailed = manageState.actionFailed,
                resumable = resumable,
                onResume = { page.push(ManageRoute.Capture(resume = route.imageId)) },
                onDelete = { manage.delete(setOf(route.imageId)) { page.pop() } },
                onWithdraw = manage::withdraw,
                onPublish = manage::publish,
                spots = row?.item?.let { SpotTagging.taggableSpots(it, travel.destinations) }.orEmpty(),
                spotName = { travel.spot(it)?.name },
                savingSpot = manageState.savingSpot,
                onSetSpot = manage::setSpot,
            )
        }

        is ManageRoute.Capture -> {
            val owner = shell.ownerId
            when (CaptureEntry.of(shell.entitlement)) {
                CaptureEntry.Capture -> if (owner != null) {
                    CreditCaptureScreen(
                        api = app.api,
                        atlas = app.atlas,
                        owner = owner,
                        currentOwner = shell::currentOwner,
                        direction = shell.direction,
                        uiLanguage = shell.uiLanguage,
                        enqueue = { request, imageId, thumbUrl -> app.captureQueue.enqueue(request, imageId, thumbUrl) },
                        queuedOperations = { app.captureQueue.creditOperationIds },
                        onOpenWallet = { page.push(AccountRoute.Membership) },
                        onDone = page::pop,
                        onCardQueued = { if (shell.nav.current == route) onCaptureQueued() },
                        resume = route.resume,
                        // Opened from 圖鑑管理 already: back is the way there.
                        onManage = if (route.resume != null) null else ({ page.move { TabShell.captureToManage(this) } }),
                    )
                }
                // Replaces this page rather than stacking on it: back from 會員
                // should not land on an empty capture screen.
                CaptureEntry.Membership -> LaunchedEffect(Unit) { page.replace(AccountRoute.Membership) }
                CaptureEntry.Resolving -> {
                    LaunchedEffect(Unit) { shell.models.account.refreshEntitlement() }
                    Column(Modifier.fillMaxSize().padding(TujiSpace.S4)) {
                        TujiIndeterminateBar(label = stringResource(R.string.study_loading))
                    }
                }
            }
        }
    }
}

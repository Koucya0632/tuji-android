package app.tuji.android.community

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuji.android.AccountRoute
import app.tuji.android.CommunityRoute
import app.tuji.android.ManageRoute
import app.tuji.android.ShellContext
import app.tuji.android.core.community.ReportTarget
import app.tuji.android.core.community.ViewerRelationship
import app.tuji.android.core.model.MemberFeature
import app.tuji.android.manage.CreateCollectionSheet

/** 物見's pages: a public card, an author, a 合集, and 物見搜尋. */
@Composable
fun CommunityHost(route: CommunityRoute, shell: ShellContext) {
    val app = shell.app
    val page = shell.from(route)
    val community = shell.models.community
    val blocked by community.blocked.collectAsStateWithLifecycle()
    val reported by community.reported.collectAsStateWithLifecycle()
    val myUid = shell.myUid
    val settings = shell.settings

    when (route) {
        is CommunityRoute.Item -> {
            val vm = remember(route.slug) {
                PublicItemViewModel(
                    slug = route.slug,
                    atlas = app.atlas,
                    saver = app.atlas,
                    audio = app.clipPlayer,
                    direction = shell.direction,
                    uiLang = shell.uiLang,
                    accent = settings.accent,
                    speech = app.speech,
                    onMutation = app.invalidator::post,
                    onNeedsMembership = shell::openMembership,
                ).also { it.open() }
            }
            val itemState by vm.state.collectAsStateWithLifecycle()
            val authorHandle = itemState.item?.author?.handle
            PublicItemScreen(
                state = itemState,
                relationship = ViewerRelationship.of(authorHandle, viewerHandle = myUid),
                authorBlocked = blocked.hides(authorHandle),
                reported = reported == ReportTarget.Item(route.slug),
                canPlay = vm.canPlay(),
                session = shell.direction.targetLanguage,
                uiLang = shell.uiLang,
                showChinese = settings.showZh,
                scores = shell.learning.scores,
                onRetry = vm::open,
                onToggleSave = {
                    // iOS #234: a non-member's 加入學習 would be refused (402), so
                    // go where that answer would have sent them. 學習中 → 停止
                    // is theirs to undo either way.
                    if (!itemState.saved && shell.locked(MemberFeature.CommunityWrite)) {
                        shell.openMembership()
                    } else {
                        vm.toggleSave()
                    }
                },
                onPlay = vm::play,
                onOpenAuthor = { page.push(CommunityRoute.Author(it)) },
                onReport = { reason -> community.report(ReportTarget.Item(route.slug), reason) },
                // Only the screen that asked leaves, and only if it is still on top.
                onBlock = { authorHandle?.let { handle -> community.block(handle) { page.pop() } } },
                onUnblock = { authorHandle?.let(community::unblock) },
                speech = app.speech,
                accent = settings.accent,
            )
        }

        is CommunityRoute.Collection -> {
            val vm = remember(route.slug, myUid) {
                CollectionDetailViewModel(
                    slug = route.slug,
                    atlas = app.atlas,
                    bookmarks = app.atlas,
                    learning = app.atlas,
                    viewerHandle = myUid,
                    blocked = { community.blockList },
                    onMutation = app.invalidator::post,
                    onNeedsMembership = shell::openMembership,
                ).also { it.open() }
            }
            val collectionState by vm.state.collectAsStateWithLifecycle()
            CollectionScreen(
                state = collectionState,
                reported = reported == ReportTarget.Collection(route.slug),
                onBack = page::pop,
                onRetry = vm::open,
                onSave = {
                    // iOS #234: a non-member's 收藏 would be refused (402), so go
                    // where that answer would have sent them first.
                    if (shell.locked(MemberFeature.CommunityWrite)) shell.openMembership() else vm.save()
                },
                onUnsave = vm::unsave,
                onLearn = vm::learnRemaining,
                onDismissError = vm::dismissError,
                onOpenItem = { page.push(CommunityRoute.Item(it)) },
                onOpenAuthor = { page.push(CommunityRoute.Author(it)) },
                onReport = { reason -> community.report(ReportTarget.Collection(route.slug), reason) },
                onEdit = { page.push(ManageRoute.CollectionEdit(it)) },
            )
        }

        is CommunityRoute.Author -> {
            var creatingCollection by remember { mutableStateOf(false) }
            val vm = remember(route.handle) {
                AuthorViewModel(handle = route.handle, atlas = app.atlas).also { it.load() }
            }
            val authorState by vm.state.collectAsStateWithLifecycle()
            AuthorScreen(
                state = authorState,
                relationship = ViewerRelationship.of(route.handle, viewerHandle = myUid)
                    ?: ViewerRelationship.Theirs,
                blocked = blocked.hides(route.handle),
                reported = reported == ReportTarget.Author(route.handle),
                onBack = page::pop,
                onRetry = vm::load,
                onOpenCollection = { page.push(CommunityRoute.Collection(it)) },
                onOpenItem = { page.push(CommunityRoute.Item(it)) },
                onReport = { reason -> community.report(ReportTarget.Author(route.handle), reason) },
                onBlock = { community.block(route.handle) { page.pop() } },
                onUnblock = { community.unblock(route.handle) },
                onEditProfile = { page.push(AccountRoute.EditProfile) },
                onCreateCollection = {
                    // A non-member's 合集 would be refused (402), so the button
                    // goes where that answer would have sent them.
                    if (shell.locked(MemberFeature.CommunityWrite)) shell.openMembership() else creatingCollection = true
                },
            )
            // A new 合集 is a draft, and this page shows only what is public —
            // so the editor opens, which is also where its cards and 公開合集 are.
            if (creatingCollection) {
                val myCollections = shell.models.myCollections
                val collectionsState by myCollections.state.collectAsStateWithLifecycle()
                CreateCollectionSheet(
                    state = collectionsState,
                    onCreate = { title, description ->
                        myCollections.create(title, description) { created ->
                            creatingCollection = false
                            page.push(ManageRoute.CollectionEdit(created.id))
                        }
                    },
                    onDismiss = { creatingCollection = false; myCollections.dismissCreateError() },
                )
            }
        }

        CommunityRoute.Search -> CommunitySearchScreen(
            vm = shell.models.communitySearch,
            onCancel = page::pop,
            onOpenCollection = { page.push(CommunityRoute.Collection(it)) },
            onOpenItem = { page.push(CommunityRoute.Item(it)) },
            onOpenAuthor = { page.push(CommunityRoute.Author(it)) },
        )
    }
}

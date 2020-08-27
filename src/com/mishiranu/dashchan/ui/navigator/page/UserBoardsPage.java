package com.mishiranu.dashchan.ui.navigator.page;

import android.net.Uri;
import android.view.Menu;
import android.view.MenuItem;
import androidx.recyclerview.widget.LinearLayoutManager;
import chan.content.model.Board;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.async.ReadUserBoardsTask;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.storage.FavoritesStorage;
import com.mishiranu.dashchan.ui.navigator.adapter.UserBoardsAdapter;
import com.mishiranu.dashchan.util.DialogMenu;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.ClickableToast;
import com.mishiranu.dashchan.widget.DividerItemDecoration;
import com.mishiranu.dashchan.widget.PullableRecyclerView;
import com.mishiranu.dashchan.widget.PullableWrapper;

public class UserBoardsPage extends ListPage implements UserBoardsAdapter.Callback,
		ReadUserBoardsTask.Callback {
	private static class RetainExtra {
		public static final ExtraFactory<RetainExtra> FACTORY = RetainExtra::new;

		public Board[] boards;
	}

	private ReadUserBoardsTask readTask;

	private UserBoardsAdapter getAdapter() {
		return (UserBoardsAdapter) getRecyclerView().getAdapter();
	}

	@Override
	protected void onCreate() {
		PullableRecyclerView recyclerView = getRecyclerView();
		recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));
		if (!C.API_LOLLIPOP) {
			float density = ResourceUtils.obtainDensity(recyclerView);
			ViewUtils.setNewPadding(recyclerView, (int) (16f * density), null, (int) (16f * density), null);
		}
		UserBoardsAdapter adapter = new UserBoardsAdapter(this, getPage().chanName);
		recyclerView.setAdapter(adapter);
		recyclerView.getWrapper().setPullSides(PullableWrapper.Side.TOP);
		recyclerView.addItemDecoration(new DividerItemDecoration(recyclerView.getContext(),
				(c, position) -> c.need(true)));
		RetainExtra retainExtra = getRetainExtra(RetainExtra.FACTORY);
		if (retainExtra.boards != null) {
			adapter.setItems(retainExtra.boards);
			restoreListPosition();
		} else {
			refreshBoards(false);
		}
	}

	@Override
	protected void onDestroy() {
		if (readTask != null) {
			readTask.cancel();
			readTask = null;
		}
	}

	@Override
	public String obtainTitle() {
		return getString(R.string.action_user_boards);
	}

	@Override
	public void onItemClick(String boardName) {
		if (boardName != null) {
			getUiManager().navigator().navigateBoardsOrThreads(getPage().chanName, boardName, 0);
		}
	}

	private static final int CONTEXT_MENU_COPY_LINK = 0;
	private static final int CONTEXT_MENU_ADD_FAVORITES = 1;

	@Override
	public boolean onItemLongClick(String boardName) {
		if (boardName != null) {
			DialogMenu dialogMenu = new DialogMenu(getContext(), id -> {
				switch (id) {
					case CONTEXT_MENU_COPY_LINK: {
						Uri uri = getChanLocator().safe(true).createBoardUri(boardName, 0);
						if (uri != null) {
							StringUtils.copyToClipboard(getContext(), uri.toString());
						}
						break;
					}
					case CONTEXT_MENU_ADD_FAVORITES: {
						FavoritesStorage.getInstance().add(getPage().chanName, boardName);
						break;
					}
				}
			});
			dialogMenu.addItem(CONTEXT_MENU_COPY_LINK, R.string.action_copy_link);
			if (!FavoritesStorage.getInstance().hasFavorite(getPage().chanName, boardName, null)) {
				dialogMenu.addItem(CONTEXT_MENU_ADD_FAVORITES, R.string.action_add_to_favorites);
			}
			dialogMenu.show(getUiManager().getConfigurationLock());
			return true;
		}
		return false;
	}

	private static final int OPTIONS_MENU_REFRESH = 0;

	@Override
	public void onCreateOptionsMenu(Menu menu) {
		menu.add(0, OPTIONS_MENU_SEARCH, 0, R.string.action_filter)
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
		menu.add(0, OPTIONS_MENU_REFRESH, 0, R.string.action_refresh)
				.setIcon(getActionBarIcon(R.attr.iconActionRefresh))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
		menu.addSubMenu(0, OPTIONS_MENU_APPEARANCE, 0, R.string.action_appearance);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case OPTIONS_MENU_REFRESH: {
				refreshBoards(!getAdapter().isRealEmpty());
				return true;
			}
		}
		return false;
	}

	@Override
	public void onSearchQueryChange(String query) {
		getAdapter().applyFilter(query);
	}

	@Override
	public void onListPulled(PullableWrapper wrapper, PullableWrapper.Side side) {
		refreshBoards(true);
	}

	private void refreshBoards(boolean showPull) {
		if (readTask != null) {
			readTask.cancel();
		}
		readTask = new ReadUserBoardsTask(getPage().chanName, this);
		readTask.executeOnExecutor(ReadUserBoardsTask.THREAD_POOL_EXECUTOR);
		if (showPull) {
			getRecyclerView().getWrapper().startBusyState(PullableWrapper.Side.TOP);
			switchView(ViewType.LIST, null);
		} else {
			getRecyclerView().getWrapper().startBusyState(PullableWrapper.Side.BOTH);
			switchView(ViewType.PROGRESS, null);
		}
	}

	@Override
	public void onReadUserBoardsSuccess(Board[] boards) {
		readTask = null;
		getRecyclerView().getWrapper().cancelBusyState();
		switchView(ViewType.LIST, null);
		getRetainExtra(RetainExtra.FACTORY).boards = boards;
		getAdapter().setItems(boards);
		getRecyclerView().scrollToPosition(0);
	}

	@Override
	public void onReadUserBoardsFail(ErrorItem errorItem) {
		readTask = null;
		getRecyclerView().getWrapper().cancelBusyState();
		if (getAdapter().isRealEmpty()) {
			switchView(ViewType.ERROR, errorItem.toString());
		} else {
			ClickableToast.show(getContext(), errorItem.toString());
		}
	}
}

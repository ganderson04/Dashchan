package com.mishiranu.dashchan.ui.navigator.adapter;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import chan.content.Chan;
import chan.util.StringUtils;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.model.GalleryItem;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.ui.navigator.manager.UiManager;
import com.mishiranu.dashchan.util.AnimationUtils;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.widget.DividerItemDecoration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class ThreadsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
	public interface Callback extends ListViewUtils.SimpleCallback<PostItem> {}

	public enum CatalogSort {
		UNSORTED(R.id.menu_unsorted, R.string.unsorted, null),
		DATE_CREATED(R.id.menu_date_created, R.string.date_created,
				(lhs, rhs) -> Long.compare(rhs.getTimestamp(), lhs.getTimestamp())),
		REPLIES(R.id.menu_replies, R.string.replies_count,
				(lhs, rhs) -> Integer.compare(rhs.getThreadPostsCount(), lhs.getThreadPostsCount()));

		public final int menuItemId;
		public final int titleResId;
		private final Comparator<PostItem> comparator;

		CatalogSort(int menuItemId, int titleResId, Comparator<PostItem> comparator) {
			this.menuItemId = menuItemId;
			this.titleResId = titleResId;
			this.comparator = comparator;
		}
	}

	private enum ViewType {THREAD, THREAD_HIDDEN, THREAD_CARD, THREAD_CARD_HIDDEN, THREAD_CELL}

	private static final int LIST_PADDING = 12;
	private static final int CARD_MIN_WIDTH_LARGE_DP = 120;
	private static final int CARD_MIN_WIDTH_SMALL_DP = 90;
	private static final int CARD_PADDING_OUT_DP = 8;
	private static final int CARD_PADDING_IN_DP = 4;
	private static final int CARD_PADDING_IN_EXTRA_DP = 1;

	private static class GridMode {
		public final int columns;
		public final boolean small;
		public final int gridItemContentHeight;

		private GridMode(int columns, boolean small, int gridItemContentHeight) {
			this.columns = columns;
			this.small = small;
			this.gridItemContentHeight = gridItemContentHeight;
		}
	}

	private final ArrayList<PostItem> postItems = new ArrayList<>();
	private ArrayList<PostItem> catalogSortedPostItems;
	private ArrayList<PostItem> filteredPostItems;
	private boolean catalog;

	private final Context context;
	private final Callback callback;
	private final UiManager uiManager;
	private final UiManager.ConfigurationSet configurationSet;

	private String filterText;
	private CatalogSort catalogSort;
	private boolean cardsMode;
	private GridMode gridMode;

	public ThreadsAdapter(Context context, Callback callback, String chanName, UiManager uiManager,
			UiManager.PostStateProvider postStateProvider, CatalogSort catalogSort) {
		this.context = context;
		this.callback = callback;
		this.uiManager = uiManager;
		configurationSet = new UiManager.ConfigurationSet(chanName, null, null, postStateProvider,
				new GalleryItem.Set(false), uiManager.dialog().createStackInstance(), null,
				false, true, false, false, false, null);
		this.catalogSort = catalogSort;
	}

	private RecyclerView.ViewHolder configureView(RecyclerView.ViewHolder viewHolder, View view) {
		return ListViewUtils.bind(viewHolder, view, true, this::getItem, callback);
	}

	private RecyclerView.ViewHolder configureView(RecyclerView.ViewHolder viewHolder) {
		return configureView(viewHolder, viewHolder.itemView);
	}

	private RecyclerView.ViewHolder configureCard(RecyclerView.ViewHolder viewHolder) {
		return configureView(viewHolder, ((ViewGroup) viewHolder.itemView).getChildAt(0));
	}

	@NonNull
	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		switch (ViewType.values()[viewType]) {
			case THREAD: {
				return configureView(uiManager.view().createThreadViewHolder(parent, configurationSet, false, false));
			}
			case THREAD_CARD: {
				return configureCard(uiManager.view().createThreadViewHolder(parent, configurationSet, true, false));
			}
			case THREAD_HIDDEN: {
				return configureView(uiManager.view().createThreadHiddenView(parent, configurationSet, false));
			}
			case THREAD_CARD_HIDDEN: {
				return configureCard(uiManager.view().createThreadHiddenView(parent, configurationSet, true));
			}
			case THREAD_CELL: {
				return configureCard(uiManager.view().createThreadViewHolder(parent, configurationSet, true, true));
			}
			default: {
				throw new IllegalStateException();
			}
		}
	}

	@Override
	public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
		PostItem postItem = getItem(position);
		switch (ViewType.values()[holder.getItemViewType()]) {
			case THREAD:
			case THREAD_CARD: {
				uiManager.view().bindThreadView(holder, postItem);
				break;
			}
			case THREAD_HIDDEN:
			case THREAD_CARD_HIDDEN: {
				uiManager.view().bindThreadHiddenView(holder, postItem);
				break;
			}
			case THREAD_CELL: {
				uiManager.view().bindThreadCellView(holder, postItem, gridMode.small, gridMode.gridItemContentHeight);
				break;
			}
		}
	}

	public UiManager.ConfigurationSet getConfigurationSet() {
		return configurationSet;
	}

	public boolean isRealEmpty() {
		return postItems.isEmpty();
	}

	@Override
	public int getItemViewType(int position) {
		PostItem postItem = getItem(position);
		return (gridMode != null ? ViewType.THREAD_CELL
				: configurationSet.postStateProvider.isHiddenResolve(postItem)
				? (cardsMode ? ViewType.THREAD_CARD_HIDDEN : ViewType.THREAD_HIDDEN)
				: (cardsMode ? ViewType.THREAD_CARD : ViewType.THREAD)).ordinal();
	}

	private List<PostItem> getPostItems() {
		return filteredPostItems != null ? filteredPostItems : catalogSortedPostItems != null
				? catalogSortedPostItems : postItems;
	}

	private PostItem getItem(int position) {
		return getPostItems().get(position);
	}

	@Override
	public int getItemCount() {
		return getPostItems().size();
	}

	public void applyItemPadding(View view, int position, int column, Rect rect) {
		float density = ResourceUtils.obtainDensity(view);
		int paddingOut = (int) (CARD_PADDING_OUT_DP * density);
		int paddingIn = (int) (CARD_PADDING_IN_DP * density);
		if (!cardsMode) {
			rect.set(0, 0, 0, 0);
		} else {
			int columns = gridMode != null ? gridMode.columns : 1;
			int left;
			int right;
			if (columns >= 2) {
				int paddingInExtra = (int) ((CARD_PADDING_IN_DP + CARD_PADDING_IN_EXTRA_DP) * density);
				int total = 2 * paddingOut + (columns - 1) * paddingInExtra;
				float average = (float) total / columns;
				left = (int) AnimationUtils.lerp(paddingOut, average - paddingOut, (float) column / (columns - 1));
				right = (int) average - left;
			} else {
				left = paddingOut;
				right = paddingOut;
			}
			boolean firstRow = position - column == 0;
			boolean lastRow = position + columns - column >= getItemCount();
			rect.set(left, firstRow ? paddingOut : paddingIn, right, lastRow ? paddingOut : 0);
		}
	}

	public DividerItemDecoration.Configuration configureDivider
			(DividerItemDecoration.Configuration configuration, int position) {
		if (cardsMode) {
			return configuration.need(false);
		} else {
			PostItem current = getItem(position);
			PostItem next = position + 1 < getItemCount() ? getItem(position + 1) : null;
			float density = ResourceUtils.obtainDensity(context);
			int padding = (int) (LIST_PADDING * density);
			int imagePadding = (int) ((10 + 64 + 10) * density);
			boolean currentImage = current.hasAttachments() &&
					!configurationSet.postStateProvider.isHiddenResolve(current);
			boolean nextImage = next != null && next.hasAttachments() &&
					!configurationSet.postStateProvider.isHiddenResolve(next);
			return configuration.need(true).horizontal(currentImage && nextImage ? imagePadding : padding, padding);
		}
	}

	public void setItems(Collection<ArrayList<PostItem>> postItemsCollection, boolean catalog) {
		postItems.clear();
		for (ArrayList<PostItem> postItems : postItemsCollection) {
			appendItemsInternal(postItems);
		}
		this.catalog = catalog;
		applyCurrentSortingAndFilter(true, true);
		notifyDataSetChanged();
	}

	public void appendItems(ArrayList<PostItem> postItems) {
		appendItemsInternal(postItems);
		applyCurrentSortingAndFilter(true, true);
		notifyDataSetChanged();
	}

	public void notifyNotModified() {
		for (PostItem postItem : postItems) {
			postItem.setHidden(PostItem.HideState.UNDEFINED, null);
		}
		notifyDataSetChanged();
	}

	private void appendItemsInternal(List<PostItem> postItems) {
		boolean displayHidden = Preferences.isDisplayHiddenThreads();
		if (postItems != null) {
			for (PostItem postItem : postItems) {
				if (displayHidden || !configurationSet.postStateProvider.isHiddenResolve(postItem)) {
					this.postItems.add(postItem);
				}
			}
		}
	}

	public void applyFilter(String text) {
		if (!StringUtils.emptyIfNull(filterText).equals(StringUtils.emptyIfNull(text))) {
			filterText = text;
			applyCurrentSortingAndFilter(false, true);
			notifyDataSetChanged();
		}
	}

	public void setCatalogSort(CatalogSort catalogSort) {
		if (this.catalogSort != catalogSort) {
			this.catalogSort = catalogSort;
			if (catalog) {
				applyCurrentSortingAndFilter(true, false);
				notifyDataSetChanged();
			}
		}
	}

	private void applyCurrentSortingAndFilter(boolean sorting, boolean filter) {
		if (sorting) {
			Comparator<PostItem> comparator = catalogSort != null ? catalogSort.comparator : null;
			if (catalog && comparator != null) {
				if (catalogSortedPostItems == null) {
					catalogSortedPostItems = new ArrayList<>(postItems);
				} else {
					catalogSortedPostItems.clear();
					catalogSortedPostItems.addAll(postItems);
				}
				Collections.sort(catalogSortedPostItems, comparator);
			} else {
				catalogSortedPostItems = null;
			}
		}
		if (sorting || filter) {
			String text = filterText;
			if (!StringUtils.isEmpty(text)) {
				if (filteredPostItems == null) {
					filteredPostItems = new ArrayList<>();
				} else {
					filteredPostItems.clear();
				}
				text = text.toLowerCase(Locale.getDefault());
				Chan chan = Chan.get(configurationSet.chanName);
				Locale locale = Locale.getDefault();
				for (PostItem postItem : (catalogSortedPostItems != null ? catalogSortedPostItems : postItems)) {
					boolean add = postItem.getSubject().toLowerCase(locale).contains(text) ||
							postItem.getComment(chan).toString().toLowerCase(locale).contains(text);
					if (add) {
						filteredPostItems.add(postItem);
					}
				}
			} else {
				filteredPostItems = null;
			}
		}
	}

	private static int calculateColumnsCount(int totalWidth, int minWidth, int paddingOut, int paddingInExtra) {
		return Math.min(Math.max(1, (totalWidth - 2 * paddingOut + paddingInExtra)
				/ (minWidth + paddingInExtra)), 6);
	}

	public int setThreadsView(Preferences.ThreadsView threadsView) {
		if (threadsView == Preferences.ThreadsView.LARGE_GRID ||
				threadsView == Preferences.ThreadsView.SMALL_GRID) {
			float density = ResourceUtils.obtainDensity(context);
			int totalWidth = (int) (context.getResources().getConfiguration().screenWidthDp * density);
			int minWidthSmall = (int) (CARD_MIN_WIDTH_SMALL_DP * density);
			int minWidthLarge = (int) (CARD_MIN_WIDTH_LARGE_DP * density);
			int paddingOut = (int) (CARD_PADDING_OUT_DP * density);
			int paddingInExtra = (int) ((CARD_PADDING_IN_DP + CARD_PADDING_IN_EXTRA_DP) * density);
			int smallColumns = calculateColumnsCount(totalWidth, minWidthSmall, paddingOut, paddingInExtra);
			int largeColumns = calculateColumnsCount(totalWidth, minWidthLarge, paddingOut, paddingInExtra);
			if (smallColumns == largeColumns) {
				smallColumns++;
			}
			boolean small = threadsView == Preferences.ThreadsView.SMALL_GRID;
			int columns = small ? smallColumns : largeColumns;
			int contentWidth = (totalWidth - 2 * paddingOut - (columns - 1) * paddingInExtra) / columns;
			int contentHeight = (int) (contentWidth * (small ? 1.35f : 1.5f));
			cardsMode = true;
			gridMode = new GridMode(columns, small, contentHeight);
			return columns;
		} else {
			cardsMode = threadsView == Preferences.ThreadsView.CARDS;
			gridMode = null;
			return 1;
		}
	}
}

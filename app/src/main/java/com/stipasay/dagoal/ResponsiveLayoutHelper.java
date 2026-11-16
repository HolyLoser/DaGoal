package com.stipasay.dagoal;

import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.GridView;
import android.widget.LinearLayout;

/** Sizes presentation from the current content window, including split-screen and IME changes. */
public final class ResponsiveLayoutHelper {
    private ResponsiveLayoutHelper() {}

    public static void attach(View root) {
        root.addOnLayoutChangeListener((v, l, t, r, b, oldL, oldT, oldR, oldB) -> {
            if (r - l != oldR - oldL) androidx.core.view.ViewCompat.requestApplyInsets(root);
            update(root);
        });
        View preview = root.findViewById(R.id.preview_container);
        if (preview != null) preview.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> fitAvatarPreview(root));
        GridLayout assets = root.findViewById(R.id.grid_assets);
        if (assets != null) assets.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> resizeAvatarCells(assets));
        root.post(() -> update(root));
    }

    public static void update(View root) {
        if (root.getId() == R.id.root_profiling_layout) {
            View avatar = root.findViewById(R.id.avatar_host_container);
            int usableHeight = root.getHeight() - root.getPaddingTop() - root.getPaddingBottom();
            if (avatar != null && usableHeight > 0) {
                int preferred = root.getResources().getDimensionPixelSize(R.dimen.avatar_host_height_large);
                setHeight(avatar, Math.min(preferred, Math.round(usableHeight * 0.20f)));
            }
            LinearLayout steps = root.findViewById(R.id.layout_step_indicator);
            if (steps != null && steps.getChildCount() > 0 && steps.getWidth() > 0) {
                int count = (steps.getChildCount() + 1) / 2;
                int available = steps.getWidth() - steps.getPaddingLeft() - steps.getPaddingRight();
                int size = Math.min(dp(root, 22), (int) (available / (count + (count - 1) * 0.35f)));
                for (int i = 0; i < steps.getChildCount(); i += 2) {
                    View node = steps.getChildAt(i);
                    ViewGroup.LayoutParams lp = node.getLayoutParams();
                    if (lp.width != size || lp.height != size) {
                        lp.width = size;
                        lp.height = size;
                        node.setLayoutParams(lp);
                    }
                }
            }
        }
        fitAvatarPreview(root);
        GridLayout grid = root.findViewById(R.id.grid_assets);
        if (grid != null) resizeAvatarCells(grid);
    }

    private static void fitAvatarPreview(View root) {
        View preview = root.findViewById(R.id.preview_container);
        View canvas = root.findViewById(R.id.avatar_square_canvas);
        if (preview == null || canvas == null || preview.getWidth() <= 0 || preview.getHeight() <= 0) return;
        int preferred = root.getResources().getDimensionPixelSize(R.dimen.avatar_preview_square_size);
        int size = Math.min(preferred, Math.min(preview.getWidth(), preview.getHeight()));
        ViewGroup.LayoutParams lp = canvas.getLayoutParams();
        if (lp.width != size || lp.height != size) {
            lp.width = size;
            lp.height = size;
            canvas.setLayoutParams(lp);
        }
    }

    public static int avatarCellSize(GridLayout grid) {
        View parent = (View) grid.getParent();
        int width = parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight();
        if (width <= 0) width = dp(grid, grid.getResources().getConfiguration().screenWidthDp)
                - 2 * grid.getResources().getDimensionPixelSize(R.dimen.spacing_normal);
        int available = width - grid.getPaddingLeft() - grid.getPaddingRight();
        return Math.max(1, Math.min(dp(grid, 100), available / 3 - dp(grid, 12)));
    }

    private static void resizeAvatarCells(GridLayout grid) {
        int size = avatarCellSize(grid);
        for (int i = 0; i < grid.getChildCount(); i++) {
            View child = grid.getChildAt(i);
            ViewGroup.LayoutParams lp = child.getLayoutParams();
            if (lp.width != size || lp.height != size) {
                lp.width = size;
                lp.height = size;
                child.setLayoutParams(lp);
            }
        }
    }

    private static int dashboardAvailableHeight(View root) {
        View main = root.findViewById(R.id.dashboard_main_layout);
        View navigation = root.findViewById(R.id.bottom_nav_container);
        int height = main != null && main.getHeight() > 0 ? main.getHeight()
                : dp(root, root.getResources().getConfiguration().screenHeightDp);
        return Math.max(1, height - (navigation != null ? navigation.getHeight() : dp(root, 96)));
    }

    public static int collapsedPanelHeight(View root) {
        return Math.min(dp(root, 330), Math.round(dashboardAvailableHeight(root) * 0.48f));
    }

    public static int expandedPanelHeight(View root) {
        View header = root.findViewById(R.id.stats_header);
        int reserved = Math.max(dp(root, 64), header != null ? header.getBottom() : 0);
        return Math.max(collapsedPanelHeight(root), Math.min(dp(root, 600), dashboardAvailableHeight(root) - reserved));
    }

    public static void attachShopHeader(View shopRoot) {
        View header = shopRoot.findViewById(R.id.shop_actions_header);
        View balance = shopRoot.findViewById(R.id.shop_balance_group);
        View refresh = shopRoot.findViewById(R.id.btn_refresh_shop);
        if (header == null || balance == null || refresh == null) return;
        View.OnLayoutChangeListener listener = (v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol) header.post(() -> fitShopHeader(shopRoot));
        };
        header.addOnLayoutChangeListener(listener);
        balance.addOnLayoutChangeListener(listener);
        refresh.addOnLayoutChangeListener(listener);
        header.post(() -> fitShopHeader(shopRoot));
    }

    public static void fitShopHeader(View shopRoot) {
        View header = shopRoot.findViewById(R.id.shop_actions_header);
        View balance = shopRoot.findViewById(R.id.shop_balance_group);
        View refresh = shopRoot.findViewById(R.id.btn_refresh_shop);
        if (header == null || balance == null || refresh == null || header.getWidth() <= 0) return;
        int unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        balance.measure(unspecified, unspecified);
        refresh.measure(unspecified, unspecified);
        int available = header.getWidth() - header.getPaddingLeft() - header.getPaddingRight();
        boolean stacked = balance.getMeasuredWidth() + refresh.getMeasuredWidth() + dp(header, 12) > available;
        androidx.constraintlayout.widget.ConstraintLayout.LayoutParams params =
                (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) refresh.getLayoutParams();
        int topToTop = stacked ? -1 : 0;
        int topToBottom = stacked ? R.id.shop_balance_group : -1;
        int startToStart = stacked ? 0 : -1;
        int endToEnd = stacked ? -1 : 0;
        int topMargin = stacked ? dp(header, 8) : 0;
        if (params.topToTop != topToTop || params.topToBottom != topToBottom
                || params.startToStart != startToStart || params.endToEnd != endToEnd
                || params.topMargin != topMargin) {
            params.topToTop = topToTop;
            params.topToBottom = topToBottom;
            params.startToStart = startToStart;
            params.endToEnd = endToEnd;
            params.topMargin = topMargin;
            refresh.setLayoutParams(params);
        }
    }

    public static void fitGrid(GridView grid, int itemHeightDp) {
        if (grid.getTag(R.id.responsive_grid_listener) == null) {
            grid.setTag(R.id.responsive_grid_listener, Boolean.TRUE);
            grid.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
                if (r - l != or - ol) fitGrid(grid, itemHeightDp);
            });
        }
        int width = grid.getWidth() - grid.getPaddingLeft() - grid.getPaddingRight();
        if (width <= 0) {
            grid.post(() -> { if (grid.getWidth() > 0) fitGrid(grid, itemHeightDp); });
            return;
        }
        int columns = Math.max(1, Math.min(6, (width + grid.getHorizontalSpacing())
                / (dp(grid, 104) + grid.getHorizontalSpacing())));
        if (grid.getNumColumns() != columns) grid.setNumColumns(columns);
        int count = grid.getAdapter() == null ? 0 : grid.getAdapter().getCount();
        int cellHeight = dp(grid, itemHeightDp);
        if (count > 0) {
            View cell = grid.getAdapter().getView(0, null, grid);
            int cellWidth = (width - (columns - 1) * grid.getHorizontalSpacing()) / columns;
            cell.measure(View.MeasureSpec.makeMeasureSpec(cellWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            cellHeight = Math.max(cellHeight, cell.getMeasuredHeight());
        }
        int rows = (count + columns - 1) / columns;
        setHeight(grid, rows * cellHeight + Math.max(0, rows - 1) * grid.getVerticalSpacing()
                + grid.getPaddingTop() + grid.getPaddingBottom());
    }

    private static void setHeight(View view, int height) {
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp.height != height) {
            lp.height = height;
            view.setLayoutParams(lp);
        }
    }

    private static int dp(View view, float value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }
}

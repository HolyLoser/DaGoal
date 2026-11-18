package com.stipasay.dagoal;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import static org.junit.Assert.*;

/** Inflates real layouts in isolated resource configurations without starting user flows. */
@RunWith(AndroidJUnit4.class)
public class ResponsiveLayoutTest {
    private static final int[][] WINDOWS = {
        {320, 480}, {320, 568}, {360, 640}, {393, 851}, {480, 960}, {640, 360}, {600, 800}
    };

    private Context context(int width, int height, float fontScale) {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Configuration config = new Configuration(target.getResources().getConfiguration());
        config.densityDpi = 160;
        config.screenWidthDp = width;
        config.screenHeightDp = height;
        config.smallestScreenWidthDp = Math.min(width, height);
        config.orientation = width > height ? Configuration.ORIENTATION_LANDSCAPE : Configuration.ORIENTATION_PORTRAIT;
        config.fontScale = fontScale;
        return new ContextThemeWrapper(target.createConfigurationContext(config), R.style.Theme_DaGoal);
    }

    private View inflate(Context context, int layout) {
        return LayoutInflater.from(context).inflate(layout, null, false);
    }

    private void layout(View root, int width, int height) {
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
    }

    private void capture(View root, String name) {
        Bitmap image = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(image));
        try (FileOutputStream stream = new FileOutputStream(new File(root.getContext().getExternalFilesDir(null), name + ".png"))) {
            assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, stream));
        } catch (Exception e) { throw new AssertionError(e); }
        image.recycle();
    }

    @Test public void formsRemainScrollableAndControlsStayInsideContent() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            int[] layouts = {R.layout.activity_main, R.layout.activity_auth_email,
                    R.layout.activity_auth_password, R.layout.activity_auth_options, R.layout.activity_sign_up};
            int[] lastIds = {R.id.tv_terms_footer, R.id.tv_go_to_signup,
                    R.id.tv_forgot_password, R.id.btn_opt_facebook, R.id.tv_go_to_login};
            for (int[] window : WINDOWS) for (float font : new float[]{1f, 1.3f}) {
                Context context = context(window[0], window[1], font);
                for (int i = 0; i < layouts.length; i++) {
                    View root = inflate(context, layouts[i]);
                    assertTrue(root instanceof ScrollView);
                    layout(root, window[0], window[1]);
                    ViewGroup content = (ViewGroup) ((ScrollView) root).getChildAt(0);
                    assertTrue("Avoid nested vertical form scrollers", content instanceof ConstraintLayout);
                    View last = root.findViewById(lastIds[i]);
                    assertNotNull(last);
                    assertTrue("Form content clipped vertically: " + layouts[i], last.getBottom() <= content.getHeight());
                    for (int childIndex = 0; childIndex < content.getChildCount(); childIndex++) {
                        View child = content.getChildAt(childIndex);
                        if (child.getVisibility() != View.VISIBLE) continue;
                        assertTrue("Form control clipped horizontally: " + context.getResources().getResourceEntryName(child.getId()) + " at " + window[0] + "x" + window[1] + " font=" + font,
                                child.getLeft() >= 0 && child.getRight() <= content.getWidth());
                    }
                    if (window[0] == 320 && window[1] == 480 && font == 1f && i == 1)
                        capture(root, "responsive-email-compact");
                }
            }
        });
    }

    @Test public void editorPreviewAndEveryAssetColumnFitWindow() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (int[] window : WINDOWS) {
                Context context = context(window[0], window[1], 1.3f);
                View root = inflate(context, R.layout.activity_avatar_creation);
                GridLayout grid = root.findViewById(R.id.grid_assets);
                layout(root, window[0], window[1]);
                for (int i = 0; i < 9; i++) {
                    ImageView cell = new ImageView(context);
                    cell.setImageResource(R.drawable.hair_category_icon);
                    GridLayout.LayoutParams lp = new GridLayout.LayoutParams(GridLayout.spec(i / 3), GridLayout.spec(i % 3));
                    lp.width = ResponsiveLayoutHelper.avatarCellSize(grid);
                    lp.height = lp.width;
                    lp.setMargins(6, 6, 6, 6);
                    grid.addView(cell, lp);
                }
                ResponsiveLayoutHelper.update(root);
                layout(root, window[0], window[1]);
                View canvas = root.findViewById(R.id.avatar_square_canvas);
                View preview = root.findViewById(R.id.preview_container);
                assertTrue("Avatar preview clipped", canvas.getWidth() <= preview.getWidth()
                        && canvas.getHeight() <= preview.getHeight() && canvas.getTop() >= 0);
                for (int i = 0; i < grid.getChildCount(); i++) {
                    View cell = grid.getChildAt(i);
                    assertTrue("Asset column clipped", cell.getLeft() >= 0 && cell.getRight() <= grid.getWidth());
                    assertTrue("Asset touch target too small", cell.getWidth() >= 48);
                }
                ImageView avatar = root.findViewById(R.id.iv_composited_avatar_preview);
                avatar.setImageBitmap(AvatarCompositor.renderAvatarBitmap(context, new AvatarConfig(), 320));
                if (window[0] == 320 && window[1] == 480) capture(root, "responsive-avatar-compact");
                if (window[0] == 640) capture(root, "responsive-avatar-landscape");
                ViewGroup sidebar = root.findViewById(R.id.sidebar_categories);
                ScrollView categoryScroll = (ScrollView) sidebar.getChildAt(0);
                ViewGroup categories = (ViewGroup) categoryScroll.getChildAt(0);
                assertEquals("All avatar categories remain available", 6, categories.getChildCount());
                int contentHeight = categories.getHeight() + sidebar.getPaddingTop() + sidebar.getPaddingBottom();
                assertTrue("Category sidebar has excess blank height", sidebar.getHeight() <= contentHeight);
                View bottomPanel = root.findViewById(R.id.bottom_panel);
                assertTrue("Category sidebar overlaps selection panel", sidebar.getBottom() <= bottomPanel.getTop());
                categoryScroll.setSmoothScrollingEnabled(false);
                categoryScroll.fullScroll(View.FOCUS_DOWN);
                View lastCategory = categories.getChildAt(categories.getChildCount() - 1);
                assertTrue("Last category cannot be reached", lastCategory.getBottom() - categoryScroll.getScrollY()
                        <= categoryScroll.getHeight());
                if (window[0] == 393) capture(sidebar, "responsive-avatar-sidebar");
            }
        });
    }

    @Test public void dashboardPanelsKeepNavigationAndAvatarInsideWindow() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (int[] window : WINDOWS) {
                Context context = context(window[0], window[1], 1.3f);
                View root = inflate(context, R.layout.activity_dashboard);
                root.findViewById(R.id.card_chest_bar).setVisibility(View.GONE);
                root.findViewById(R.id.panel_wardrobe_sidebar).setVisibility(View.VISIBLE);
                layout(root, window[0], window[1]);
                int collapsed = ResponsiveLayoutHelper.collapsedPanelHeight(root);
                int expanded = ResponsiveLayoutHelper.expandedPanelHeight(root);
                View content = root.findViewById(R.id.dashboard_content_frame);
                ConstraintLayout.LayoutParams lp = (ConstraintLayout.LayoutParams) content.getLayoutParams();
                lp.height = collapsed;
                content.setLayoutParams(lp);
                View anchor = root.findViewById(R.id.view_collapsed_surface_anchor);
                ConstraintLayout.LayoutParams anchorLp = (ConstraintLayout.LayoutParams) anchor.getLayoutParams();
                anchorLp.bottomMargin = collapsed;
                anchor.setLayoutParams(anchorLp);
                layout(root, window[0], window[1]);
                View nav = root.findViewById(R.id.bottom_nav_container);
                View header = root.findViewById(R.id.stats_header);
                View host = root.findViewById(R.id.avatar_host_container);
                assertTrue("Collapsed panel covers header", content.getTop() >= header.getBottom());
                assertTrue("Navigation outside window", nav.getBottom() <= window[1]);
                TextView wardrobeLabel = root.findViewById(R.id.tv_nav_wardrobe);
                assertEquals("Navigation label must fit one line", 1, wardrobeLabel.getLineCount());
                assertTrue("Navigation label cut off", wardrobeLabel.getPaint().measureText(wardrobeLabel.getText().toString())
                        <= wardrobeLabel.getWidth());
                assertTrue("Avatar has no usable space", host.getHeight() > 0 && host.getWidth() > 0);
                assertTrue(expanded >= collapsed);
                assertTrue(expanded <= window[1] - nav.getHeight());
                ImageView avatar = new ImageView(context);
                avatar.setImageBitmap(AvatarCompositor.renderAvatarBitmap(context, new AvatarConfig(), 320));
                avatar.setScaleType(ImageView.ScaleType.FIT_CENTER);
                ((FrameLayout) host).addView(avatar, new FrameLayout.LayoutParams(-1, -1));
                layout(root, window[0], window[1]);
                if (window[0] == 320 && window[1] == 480) capture(root, "responsive-dashboard-compact");
                if (window[0] == 640) capture(root, "responsive-dashboard-landscape");
            }
        });
    }

    @Test public void keyboardInsetsDoNotStackAndQuestionnaireStillScrolls() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context context = context(320, 568, 1.3f);
            View form = inflate(context, R.layout.activity_auth_email);
            AppearanceHelper.setupEdgeToEdgeInsets(form);
            layout(form, 320, 568);
            int originalPadding = 24;
            WindowInsetsCompat insets = new WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 24, 0, 24))
                    .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 240)).build();
            ViewCompat.dispatchApplyWindowInsets(form, insets);
            assertEquals(originalPadding + 240, form.getPaddingBottom());
            ViewCompat.dispatchApplyWindowInsets(form, insets);
            assertEquals("Insets accumulated", originalPadding + 240, form.getPaddingBottom());
            View root = inflate(context, R.layout.activity_profiling_questionnaire);
            LinearLayout steps = root.findViewById(R.id.layout_step_indicator);
            for (int i = 0; i < 23; i++) {
                TextView node = new TextView(context);
                steps.addView(node, new LinearLayout.LayoutParams(i % 2 == 0 ? 22 : 0, 22, i % 2 == 0 ? 0 : 1));
            }
            layout(root, 320, 568);
            ResponsiveLayoutHelper.update(root);
            layout(root, 320, 568);
            View question = root.findViewById(R.id.frame_question_anim_host);
            assertTrue("Question area squeezed out", question.getHeight() > 200);
            View avatar = root.findViewById(R.id.avatar_host_container);
            assertTrue(avatar.getBottom() <= 568);
            TextView option = root.findViewById(R.id.tv_option_text_1);
            option.setText("Improve focus on school and work with a healthier daily routine");
            layout(root, 320, 568);
            assertTrue("Answer text clipped", option.getHeight() >= option.getLayout().getHeight());
            capture(root, "responsive-questionnaire-compact");
        });
    }

    @Test public void tallDialogsCanScrollOnShortWindows() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context context = context(320, 480, 1.3f);
            int[] dialogs = {R.layout.dialog_shop_preview, R.layout.dialog_achievement_detail,
                    R.layout.dialog_first_run_permissions, R.layout.dialog_app_tutorial,
                    R.layout.dialog_chest_loot_reveal, R.layout.dialog_delete_account,
                    R.layout.dialog_reauth_delete, R.layout.dialog_signup_sync,
                    R.layout.dialog_eye_color_picker, R.layout.dialog_hair_color_picker};
            for (int resource : dialogs) {
                View root = inflate(context, resource);
                assertTrue(root instanceof ScrollView);
                root.measure(View.MeasureSpec.makeMeasureSpec(288, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.AT_MOST));
                root.layout(0, 0, 288, root.getMeasuredHeight());
                assertTrue(root.getHeight() <= 360);
                assertTrue(((ScrollView) root).getChildAt(0).getHeight() > 0);
            }
        });
    }

    @Test public void shopGridAdaptsColumnsAndMeasuresLargeTextRows() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (int[] window : WINDOWS) {
                Context context = context(window[0], window[1], 1.3f);
                View root = inflate(context, R.layout.view_dashboard_shop);
                android.widget.GridView grid = root.findViewById(R.id.grid_shop_items);
                grid.setAdapter(new android.widget.BaseAdapter() {
                    @Override public int getCount() { return 12; }
                    @Override public Object getItem(int position) { return position; }
                    @Override public long getItemId(int position) { return position; }
                    @Override public View getView(int position, View old, ViewGroup parent) {
                        View cell = LayoutInflater.from(context).inflate(R.layout.item_shop_grid, parent, false);
                        ((TextView) cell.findViewById(R.id.tv_shop_item_name)).setText("Royal Cupcake Dress");
                        return cell;
                    }
                });
                layout(root, window[0], window[1]);
                ResponsiveLayoutHelper.fitGrid(grid, 142);
                layout(root, window[0], window[1]);
                assertTrue(grid.getNumColumns() >= 2);
                assertTrue(grid.getNumColumns() <= 6);
                for (int i = 0; i < grid.getChildCount(); i++) {
                    View cell = grid.getChildAt(i);
                    assertTrue("Shop card clipped horizontally", cell.getLeft() >= 0 && cell.getRight() <= grid.getWidth());
                    assertTrue("Shop row clipped vertically", cell.getBottom() <= grid.getHeight());
                }
                if (window[0] == 320 && window[1] == 480) capture(root, "responsive-shop-compact");
            }
        });
    }

    @Test public void chestCardDoesNotStretchPastItsContents() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (int[] window : WINDOWS) {
                Context context = context(window[0], window[1], 1.3f);
                View root = inflate(context, R.layout.activity_dashboard);
                root.findViewById(R.id.panel_wardrobe_sidebar).setVisibility(View.GONE);
                layout(root, window[0], window[1]);
                int collapsed = ResponsiveLayoutHelper.collapsedPanelHeight(root);
                View content = root.findViewById(R.id.dashboard_content_frame);
                ViewGroup.LayoutParams contentParams = content.getLayoutParams();
                contentParams.height = collapsed;
                content.setLayoutParams(contentParams);
                View anchor = root.findViewById(R.id.view_collapsed_surface_anchor);
                ConstraintLayout.LayoutParams anchorParams = (ConstraintLayout.LayoutParams) anchor.getLayoutParams();
                anchorParams.bottomMargin = collapsed;
                anchor.setLayoutParams(anchorParams);
                TextView points = root.findViewById(R.id.tv_chest_bar_points);
                points.setText("10/10");
                layout(root, window[0], window[1]);
                ScrollView card = root.findViewById(R.id.card_chest_bar);
                View inner = card.getChildAt(0);
                assertTrue("Chest card has excess blank height", card.getHeight()
                        <= inner.getHeight() + card.getPaddingTop() + card.getPaddingBottom());
                View panel = root.findViewById(R.id.panel_avatar_host);
                assertTrue("Chest card extends beyond avatar panel", card.getBottom() <= panel.getHeight());
                View icon = root.findViewById(R.id.iv_chest_bar_icon);
                assertTrue("Chest icon clipped horizontally", icon.getWidth()
                        <= card.getWidth() - card.getPaddingLeft() - card.getPaddingRight());
                assertEquals(1, points.getLineCount());
                assertTrue("Chest count text clipped", points.getPaint().measureText("10/10") <= points.getWidth());
                if (window[0] == 393) capture(card, "responsive-chest-card");
            }
        });
    }

    @Test public void shopActionsShareRowWhenTheyFitAndNeverOverlap() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (int[] window : WINDOWS) for (float font : new float[]{1f, 1.3f}) {
                Context context = context(window[0], window[1], font);
                View root = inflate(context, R.layout.view_dashboard_shop);
                TextView gold = root.findViewById(R.id.tv_shop_gold_balance);
                gold.setText("1.24B");
                TextView badge = root.findViewById(R.id.tv_shop_status_badge);
                android.widget.Button refresh = root.findViewById(R.id.btn_refresh_shop);
                for (boolean showBadge : new boolean[]{false, true}) {
                    badge.setVisibility(showBadge ? View.VISIBLE : View.GONE);
                    for (int state = 0; state < 3; state++) {
                        boolean maxed = state == 2;
                        refresh.setText(state == 0 ? context.getString(R.string.shop_refresh_gold, 30, 0)
                                : state == 1 ? context.getString(R.string.shop_refresh_token, 4)
                                : context.getString(R.string.shop_refresh_maxed));
                        refresh.setEnabled(!maxed);
                        layout(root, window[0], window[1]);
                        ResponsiveLayoutHelper.fitShopHeader(root);
                        layout(root, window[0], window[1]);
                        View balance = root.findViewById(R.id.shop_balance_group);
                        View header = root.findViewById(R.id.shop_actions_header);
                        assertTrue("Gold and refresh overlap", refresh.getLeft() >= balance.getRight()
                                || refresh.getTop() >= balance.getBottom());
                        assertTrue("Refresh outside available width", refresh.getLeft() >= 0
                                && refresh.getRight() <= header.getWidth());
                        int needed = balance.getWidth() + refresh.getWidth() + 12;
                        if (needed <= header.getWidth()) {
                            assertEquals("Shop controls unnecessarily stacked", balance.getTop(), refresh.getTop());
                        }
                        assertEquals("Maxed refresh state changed", !maxed, refresh.isEnabled());
                        assertEquals("Gold balance changed", "1.24B", gold.getText().toString());
                        if (window[0] == 320 && window[1] == 480 && maxed && !showBadge)
                            capture(header, font == 1f ? "responsive-shop-actions" : "responsive-shop-actions-large-font");
                        if (window[0] == 320 && window[1] == 480 && state == 0 && !showBadge)
                            capture(header, font == 1f ? "responsive-shop-refresh" : "responsive-shop-refresh-large-font");
                    }
                }
            }
        });
    }
}

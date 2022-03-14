package com.capacitorjs.plugins.keyboard;

import android.content.Context;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import com.getcapacitor.Logger;

@RequiresApi(21)
public class Keyboard {

    interface KeyboardEventListener {
        void onKeyboardEvent(String event, int size);
    }

    private AppCompatActivity activity;
    private ViewTreeObserver.OnGlobalLayoutListener list;
    private View rootView;
    private View mChildOfContent;
    private int usableHeightPrevious;
    private FrameLayout.LayoutParams frameLayoutParams;

    @Nullable
    public KeyboardEventListener getKeyboardEventListener() {
        return keyboardEventListener;
    }

    public void setKeyboardEventListener(@Nullable KeyboardEventListener keyboardEventListener) {
        this.keyboardEventListener = keyboardEventListener;
    }

    @Nullable
    private KeyboardEventListener keyboardEventListener;

    static final String EVENT_KB_WILL_SHOW = "keyboardWillShow";
    static final String EVENT_KB_DID_SHOW = "keyboardDidShow";
    static final String EVENT_KB_WILL_HIDE = "keyboardWillHide";
    static final String EVENT_KB_DID_HIDE = "keyboardDidHide";

    /**
     * @deprecated
     * Use {@link #Keyboard(AppCompatActivity activity, boolean resizeFullScreen)}
     * @param activity
     */
    public Keyboard(AppCompatActivity activity) {
        this(activity, false);
    }

    public Keyboard(AppCompatActivity activity, boolean resizeOnFullScreen) {
        this.activity = activity;
        //calculate density-independent pixels (dp)
        //http://developer.android.com/guide/practices/screens_support.html
        DisplayMetrics dm = new DisplayMetrics();
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            activity.getDisplay().getRealMetrics(dm);
        } else {
            activity.getWindowManager().getDefaultDisplay().getMetrics(dm);
        }
        final float density = dm.density;

        //http://stackoverflow.com/a/4737265/1091751 detect if keyboard is showing
        FrameLayout content = activity.getWindow().getDecorView().findViewById(android.R.id.content);
        rootView = content.getRootView();
        list =
            new ViewTreeObserver.OnGlobalLayoutListener() {
                int previousHeightDiff = 0;

                @Override
                public void onGlobalLayout() {
                    if (resizeOnFullScreen) {
                        possiblyResizeChildOfContent();
                    }
                    Rect r = new Rect();
                    //r will be populated with the coordinates of your view that area still visible.
                    rootView.getWindowVisibleDisplayFrame(r);

                    // cache properties for later use
                    int rootViewHeight = rootView.getRootView().getHeight();
                    int resultBottom = r.bottom;
                    int screenHeight;

                    if (Build.VERSION.SDK_INT >= 30) {
                        Insets windowInsets = rootView.getRootWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemBars());
                        screenHeight = rootViewHeight;
                        resultBottom = windowInsets.bottom;
                    } else {
                        WindowInsets windowInsets = rootView.getRootWindowInsets();
                        int stableInsetBottom = windowInsets.getStableInsetBottom();
                        screenHeight = rootViewHeight;
                        resultBottom = resultBottom + stableInsetBottom;
                    }

                    int heightDiff = screenHeight - resultBottom;

                    int pixelHeightDiff = (int) (heightDiff / density);
                    if (keyboardEventListener != null) {
                        if (pixelHeightDiff > 100 && pixelHeightDiff != previousHeightDiff) { // if more than 100 pixels, its probably a keyboard...
                            keyboardEventListener.onKeyboardEvent(EVENT_KB_WILL_SHOW, pixelHeightDiff);
                            keyboardEventListener.onKeyboardEvent(EVENT_KB_DID_SHOW, pixelHeightDiff);
                        } else if (pixelHeightDiff != previousHeightDiff && (previousHeightDiff - pixelHeightDiff) > 100) {
                            keyboardEventListener.onKeyboardEvent(EVENT_KB_WILL_HIDE, 0);
                            keyboardEventListener.onKeyboardEvent(EVENT_KB_DID_HIDE, 0);
                        }
                    } else {
                        Logger.warn("Native Keyboard Event Listener not found");
                    }
                    previousHeightDiff = pixelHeightDiff;
                }

                private void possiblyResizeChildOfContent() {
                    int usableHeightNow = computeUsableHeight();
                    if (usableHeightPrevious != usableHeightNow) {
                        frameLayoutParams.height = usableHeightNow;
                        mChildOfContent.requestLayout();
                        usableHeightPrevious = usableHeightNow;
                    }
                }

                private int computeUsableHeight() {
                    Rect r = new Rect();
                    mChildOfContent.getWindowVisibleDisplayFrame(r);
                    return isOverlays() ? r.bottom : r.height();
                }

                private boolean isOverlays() {
                    final Window window = activity.getWindow();
                    return window.getDecorView().getFitsSystemWindows();
                }
            };
        mChildOfContent = content.getChildAt(0);
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(list);
        frameLayoutParams = (FrameLayout.LayoutParams) mChildOfContent.getLayoutParams();
    }

    public void show() {
        ((InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(activity.getCurrentFocus(), 0);
    }

    public boolean hide() {
        InputMethodManager inputManager = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        View v = activity.getCurrentFocus();
        if (v == null) {
            return false;
        } else {
            inputManager.hideSoftInputFromWindow(v.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
            return true;
        }
    }
}

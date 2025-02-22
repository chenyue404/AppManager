// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.dialog;

import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import androidx.annotation.CallSuper;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.bottomsheet.BottomSheetDragHandleView;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Objects;

import io.github.muntashirakon.ui.R;
import io.github.muntashirakon.util.UiUtils;

/**
 * A {@link BottomSheetDialogFragment} with a capsule on top. This is a widely used design but seems to be missing
 * from the Material Components library.
 */
// Copyright 2022 Muntashir Al-Islam
// Copyright 2022 Absinthe
public abstract class CapsuleBottomSheetDialogFragment extends BottomSheetDialogFragment
        implements View.OnLayoutChangeListener {
    public static final String TAG = CapsuleBottomSheetDialogFragment.class.getSimpleName();

    private LinearLayoutCompat mBottomSheetContainer;
    private BottomSheetDragHandleView mDragHandle;
    private LinearLayoutCompat mHeaderContainer;
    private FrameLayout mMainContainer;
    private LinearLayoutCompat mBodyContainer;
    private RelativeLayout mLoadingLayout;
    @Nullable
    private View mHeader;
    private View mBody;
    private boolean mIsCapsuleActivated;
    private boolean mIsLoadingFinished;
    private BottomSheetBehavior<FrameLayout> mBehavior;
    @Px
    private int mMaxHeight;
    @Px
    private int mMaxPeekHeight;

    private final BottomSheetBehavior.BottomSheetCallback mBottomSheetCallback = new BottomSheetBehavior.BottomSheetCallback() {
        @Override
        public void onStateChanged(@NonNull View bottomSheet, int newState) {
            switch (newState) {
                case BottomSheetBehavior.STATE_DRAGGING:
                    if (!mIsCapsuleActivated) {
                        mIsCapsuleActivated = true;
                        onCapsuleActivated(true);
                    }
                    break;
                case BottomSheetBehavior.STATE_COLLAPSED:
                    if (mIsCapsuleActivated) {
                        mIsCapsuleActivated = false;
                        onCapsuleActivated(false);
                    }
                    break;
                case BottomSheetBehavior.STATE_EXPANDED:
                    if (mIsCapsuleActivated) {
                        mIsCapsuleActivated = false;
                        onCapsuleActivated(false);
                    }
                    // Workaround for rounded corners in the bottom sheet
                    bottomSheet.setBackground(createMaterialShapeDrawable(bottomSheet));
                case BottomSheetBehavior.STATE_HALF_EXPANDED:
                case BottomSheetBehavior.STATE_HIDDEN:
                case BottomSheetBehavior.STATE_SETTLING:
                    break;
            }
        }

        @Override
        public void onSlide(@NonNull View bottomSheet, float slideOffset) {
        }
    };

    public View getBody() {
        return mBody;
    }

    @MainThread
    @NonNull
    public abstract View initRootView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState);

    @MainThread
    public void onBodyInitialized(@NonNull View bodyView, @Nullable Bundle savedInstanceState) {
    }

    @MainThread
    public boolean displayLoaderByDefault() {
        return false;
    }

    @MainThread
    public void startLoading() {
        if (!mIsLoadingFinished) {
            return;
        }
        mIsLoadingFinished = false;
        mLoadingLayout.setVisibility(View.VISIBLE);
        mBodyContainer.setVisibility(View.GONE);
    }

    @MainThread
    public void finishLoading() {
        if (mIsLoadingFinished) {
            return;
        }
        mIsLoadingFinished = true;
        mBodyContainer.setVisibility(View.VISIBLE);
        if (mBodyContainer.getChildCount() != 1) {
            mBodyContainer.addView(getBody(), new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } // else Body has already been set, no need to set it again
        mLoadingLayout.setVisibility(View.GONE);
    }

    @Nullable
    public View getHeader() {
        return mHeader;
    }

    public void setHeader(@Nullable View header) {
        mHeader = header;
        mHeaderContainer.removeAllViews();
        if (header != null) {
            // Remove top padding
            // TODO: 12/8/22 Fix this workaround by unsetting the top padding in the DialogTitleBuilder
            if (header.isPaddingRelative()) {
                header.setPaddingRelative(header.getPaddingStart(), 0, header.getPaddingEnd(), header.getPaddingBottom());
            } else {
                header.setPadding(header.getPaddingStart(), 0, header.getPaddingEnd(), header.getPaddingBottom());
            }
            mHeaderContainer.addView(header, new LinearLayoutCompat.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    @Px
    public int getMaxHeight() {
        return mMaxHeight;
    }

    public void setMaxHeight(@Px int maxHeight) {
        mMaxHeight = maxHeight;
        if (mBehavior != null) {
            mBehavior.setMaxHeight(maxHeight);
        }
    }

    @Px
    public int getMaxPeekHeight() {
        return mMaxPeekHeight;
    }

    public void setMaxPeekHeight(@Px int maxPeekHeight) {
        mMaxPeekHeight = maxPeekHeight;
    }

    @CallSuper
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = new BottomSheetDialogInternal(requireContext(), getTheme());
        mBehavior = dialog.getBehavior();
        mBehavior.setSkipCollapsed(true);
        if (mMaxHeight != 0) {
            mBehavior.setMaxHeight(mMaxHeight);
        } else {
            mMaxHeight = mBehavior.getMaxHeight();
        }
        return dialog;
    }

    @NonNull
    @Override
    public final View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mBottomSheetContainer = (LinearLayoutCompat) inflater.inflate(R.layout.dialog_bottom_sheet_capsule, container, false);
        mDragHandle = mBottomSheetContainer.findViewById(R.id.capsule);
        mDragHandle.setImageDrawable(new TransitionDrawable(new Drawable[]{
                ContextCompat.getDrawable(requireContext(), R.drawable.bottom_sheet_drag_handle),
                ContextCompat.getDrawable(requireContext(), R.drawable.bottom_sheet_drag_handle_activated)
        }));
        mHeaderContainer = mBottomSheetContainer.findViewById(R.id.header);
        mBodyContainer = mBottomSheetContainer.findViewById(R.id.body);
        mLoadingLayout = mBottomSheetContainer.findViewById(R.id.loader);
        mMainContainer = (FrameLayout) mBodyContainer.getParent();
        mBody = initRootView(inflater, mBottomSheetContainer, savedInstanceState);

        if (!displayLoaderByDefault()) {
            finishLoading();
        }
        return mBottomSheetContainer;
    }

    @CallSuper
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        onBodyInitialized(mBody, savedInstanceState);
    }

    @CallSuper
    @Override
    public void onStart() {
        super.onStart();
        mBehavior.addBottomSheetCallback(mBottomSheetCallback);
        mBottomSheetContainer.addOnLayoutChangeListener(this);

        mBottomSheetContainer.post(() -> {
            try {
                Method setStateInternal = BottomSheetBehavior.class.getDeclaredMethod("setStateInternal", int.class);
                setStateInternal.setAccessible(true);
                setStateInternal.invoke(mBehavior, BottomSheetBehavior.STATE_EXPANDED);
            } catch (Throwable ignore) {
            }
        });
    }

    @CallSuper
    @Override
    public void onStop() {
        super.onStop();
        mBehavior.removeBottomSheetCallback(mBottomSheetCallback);
        mBottomSheetContainer.removeOnLayoutChangeListener(this);
    }

    @CallSuper
    @Override
    public void onDestroyView() {
        mHeader = null;
        mBody = null;
        super.onDestroyView();
    }

    @CallSuper
    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
        int oldHeight = oldBottom - oldTop;
        int newHeight = bottom - top;
        if (newHeight != oldHeight) {
            Log.d(TAG, String.format(Locale.ROOT, "onLayoutChange: %d -> %d", oldHeight, newHeight));
            updateDialogHeight(newHeight);
        }
    }

    @NonNull
    private Drawable createMaterialShapeDrawable(@NonNull View bottomSheet) {
        // Create a ShapeAppearanceModel with the same shapeAppearanceOverlay used in the style
        ShapeAppearanceModel shapeAppearanceModel = ShapeAppearanceModel.builder(requireContext(),
                0, R.style.ShapeAppearance_AppTheme_MediumComponent_RoundedTop).build();

        // Create a new MaterialShapeDrawable (you can't use the original MaterialShapeDrawable in the BottomSheet)
        MaterialShapeDrawable currentMaterialShapeDrawable = (MaterialShapeDrawable) bottomSheet.getBackground();
        MaterialShapeDrawable newMaterialShapeDrawable = new MaterialShapeDrawable(shapeAppearanceModel);

        // Copy the attributes in the new MaterialShapeDrawable
        newMaterialShapeDrawable.initializeElevationOverlay(requireContext());
        newMaterialShapeDrawable.setFillColor(currentMaterialShapeDrawable.getFillColor());
        newMaterialShapeDrawable.setTintList(currentMaterialShapeDrawable.getTintList());
        newMaterialShapeDrawable.setElevation(currentMaterialShapeDrawable.getElevation());
        newMaterialShapeDrawable.setStrokeWidth(currentMaterialShapeDrawable.getStrokeWidth());
        newMaterialShapeDrawable.setStrokeColor(currentMaterialShapeDrawable.getStrokeColor());
        return newMaterialShapeDrawable;
    }

    private void updateDialogHeight(int newHeight) {
        if (mMaxPeekHeight != 0 && newHeight > mMaxPeekHeight) {
            newHeight = mMaxPeekHeight;
        }
        mBehavior.setPeekHeight(newHeight, true);
    }

    public void onCapsuleActivated(boolean activated) {
        if (activated) {
            ((TransitionDrawable) mDragHandle.getDrawable()).startTransition(150);
        } else {
            ((TransitionDrawable) mDragHandle.getDrawable()).reverseTransition(150);
        }
    }

    private static class BottomSheetDialogInternal extends BottomSheetDialog {
        public BottomSheetDialogInternal(@NonNull Context context, int theme) {
            super(context, theme);
        }

        @Override
        public void onAttachedToWindow() {
            super.onAttachedToWindow();
            Window window = getWindow();
            if (window != null) {
                window.getAttributes().windowAnimations = R.style.AppTheme_BottomSheetAnimation;
                WindowCompat.setDecorFitsSystemWindows(window, false);
                UiUtils.setSystemBarStyle(window, true);
                new WindowInsetsControllerCompat(window, window.getDecorView())
                        .setAppearanceLightNavigationBars(!UiUtils.isDarkMode());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                    window.getAttributes().setBlurBehindRadius(64);
                    window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                }
            }

            Objects.requireNonNull((View) findViewById(R.id.container)).setFitsSystemWindows(false);
            Objects.requireNonNull((View) findViewById(R.id.coordinator)).setFitsSystemWindows(false);
        }
    }
}

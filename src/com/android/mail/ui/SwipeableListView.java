/*
 * Copyright (C) 2012 Google Inc.
 * Licensed to The Android Open Source Project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.mail.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ListAdapter;
import android.widget.ListView;

import com.android.mail.R;
import com.android.mail.browse.ConversationItemView;
import com.android.mail.providers.Conversation;
import com.android.mail.ui.SwipeHelper.Callback;
import com.android.mail.utils.LogUtils;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Collection;

public class SwipeableListView extends ListView implements Callback {
    private SwipeHelper mSwipeHelper;
    private SwipeCompleteListener mSwipeCompleteListener;
    private boolean mEnableSwipe = false;
    private ListAdapter mDebugAdapter;
    private int mDebugLastCount;

    // TODO: remove me and all enclosed blocks when b/6255909 is fixed
    private static final boolean DEBUG_LOGGING_CONVERSATION_CURSOR = true;

    public static final String LOG_TAG = new LogUtils().getLogTag();

    private ConversationSelectionSet mConvSelectionSet;

    public SwipeableListView(Context context) {
        this(context, null);
    }

    public SwipeableListView(Context context, AttributeSet attrs) {
        this(context, attrs, -1);
    }

    public SwipeableListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        float densityScale = getResources().getDisplayMetrics().density;
        float scrollSlop = context.getResources().getInteger(R.integer.swipeScrollSlop);
        float minSwipe = context.getResources().getDimension(R.dimen.min_swipe);
        float minVert = context.getResources().getDimension(R.dimen.min_vert);
        float minLock = context.getResources().getDimension(R.dimen.min_lock);
        mSwipeHelper = new SwipeHelper(SwipeHelper.X, this, densityScale, densityScale,
                scrollSlop, minSwipe, minVert, minLock);
    }

    /**
     * Enable swipe gestures.
     */
    public void enableSwipe(boolean enable) {
        mEnableSwipe = enable;
    }

    public boolean isSwipeEnabled() {
        return mEnableSwipe;
    }

    public void setSwipeCompleteListener(SwipeCompleteListener listener) {
        mSwipeCompleteListener = listener;
    }

    public void setSelectionSet(ConversationSelectionSet set) {
        mConvSelectionSet = set;
    }

    @Override
    public ConversationSelectionSet getSelectionSet() {
        return mConvSelectionSet;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mEnableSwipe) {
            return mSwipeHelper.onInterceptTouchEvent(ev)
                    || super.onInterceptTouchEvent(ev);
        } else {
            return super.onInterceptTouchEvent(ev);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mEnableSwipe) {
            return mSwipeHelper.onTouchEvent(ev) || super.onTouchEvent(ev);
        } else {
            return super.onTouchEvent(ev);
        }
    }

    @Override
    public void setAdapter(ListAdapter adapter) {
        super.setAdapter(adapter);
        if (DEBUG_LOGGING_CONVERSATION_CURSOR) {
            mDebugAdapter = adapter;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (DEBUG_LOGGING_CONVERSATION_CURSOR) {
            final int count = mDebugAdapter == null ? 0 : mDebugAdapter.getCount();
            if (count != mDebugLastCount) {
                LogUtils.i(LOG_TAG, "Conversation ListView about to change mItemCount to: %d",
                        count);
                mDebugLastCount = count;
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void layoutChildren() {
        if (DEBUG_LOGGING_CONVERSATION_CURSOR) {
            LogUtils.i(LOG_TAG, "Conversation ListView may compare last mItemCount to new val: %d",
                    mDebugAdapter == null ? 0 : mDebugAdapter.getCount());
        }
        super.layoutChildren();
    }

    @Override
    public View getChildAtPosition(MotionEvent ev) {
        // find the view under the pointer, accounting for GONE views
        final int count = getChildCount();
        int touchY = (int) ev.getY();
        int childIdx = 0;
        View slidingChild;
        for (; childIdx < count; childIdx++) {
            slidingChild = getChildAt(childIdx);
            if (slidingChild.getVisibility() == GONE) {
                continue;
            }
            if (touchY >= slidingChild.getTop() && touchY <= slidingChild.getBottom()) {
                return slidingChild;
            }
        }
        return null;
    }

    @Override
    public View getChildContentView(View v) {
        return v;
    }

    @Override
    public boolean canChildBeDismissed(View v) {
        return v instanceof ConversationItemView;
    }

    @Override
    public void onChildDismissed(View v) {
        dismissChildren(ImmutableList.of(getConversation(v)));
    }

    @Override
    public void onChildrenDismissed(Collection<ConversationItemView> views) {
        final ArrayList<Conversation> conversations = new ArrayList<Conversation>();
        for (ConversationItemView view : views) {
            conversations.add(getConversation(view));
        }
        dismissChildren(conversations);
    }

    private Conversation getConversation(View view) {
        Conversation c = ((ConversationItemView) view).getConversation();
        if (view.getParent() == null) {
            return c;
        }
        c.position = getPositionForView(view);
        return c;
    }

    private void dismissChildren(final Collection<Conversation> conversations) {
        AnimatedAdapter adapter = ((AnimatedAdapter) getAdapter());
        adapter.delete(conversations, new ActionCompleteListener() {
            @Override
            public void onActionComplete() {
                mSwipeCompleteListener.onSwipeComplete(conversations);
            }
        });
    }

    @Override
    public void onBeginDrag(View v) {
        // We do this so the underlying ScrollView knows that it won't get
        // the chance to intercept events anymore
        requestDisallowInterceptTouchEvent(true);
        // If there are selected conversations, we are dismissing an entire
        // associated set.
        // Otherwise, the SwipeHelper will just get rid of the single item it
        // received touch events for.
        mSwipeHelper.setAssociatedViews(mConvSelectionSet != null ? mConvSelectionSet.views()
                : null);
    }

    @Override
    public void onDragCancelled(View v) {
        mSwipeHelper.setAssociatedViews(null);
    }

    public interface SwipeCompleteListener {
        public void onSwipeComplete(Collection<Conversation> conversations);
    }
}

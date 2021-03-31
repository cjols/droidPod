package com.example.droidpod;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

public class CustomTouchListener implements RecyclerView.OnItemTouchListener {

    GestureDetector gestureDetector;
    private onItemClickListener listener;

    public CustomTouchListener(Context mContext, final onItemClickListener mListener) {
        this.listener = mListener;
        gestureDetector = new GestureDetector(mContext, new GestureDetector.SimpleOnGestureListener() {

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                return true;
            }
        });
    }

    @Override
    public boolean onInterceptTouchEvent(RecyclerView recyclerView, MotionEvent e) {
        View child = recyclerView.findChildViewUnder(e.getX(), e.getY());
        if (child != null && listener != null && gestureDetector.onTouchEvent(e)) {
            listener.onClick(child, recyclerView.getChildLayoutPosition(child));
        }
        return false;
    }

    @Override
    public void onTouchEvent(RecyclerView view, MotionEvent e) { }

    @Override
    public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) { }
}

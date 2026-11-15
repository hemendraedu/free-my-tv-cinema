package io.freemytv;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.ScrollView;

/** One retargetable scroll animation instead of ScrollView's focus jumps and competing smooth-scroll calls. */
final class CinemaScroll extends ScrollView {
    private ValueAnimator motion;
    private View latestFocus;
    boolean rebuilding;
    private final Runnable reveal = () -> revealFocus();
    CinemaScroll(Context context) { super(context); setOverScrollMode(OVER_SCROLL_NEVER); }
    @Override protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) { return 0; }
    @Override public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) { return false; }
    @Override public void requestChildFocus(View child, View focused) {
        super.requestChildFocus(child, focused);
        latestFocus=focused;removeCallbacks(reveal);
        if(!rebuilding)postOnAnimation(reveal);
    }
    private void revealFocus() {
        if(rebuilding||latestFocus==null||!latestFocus.isAttachedToWindow())return;
        Rect rect=new Rect();latestFocus.getDrawingRect(rect);offsetDescendantRectToMyCoords(latestFocus,rect);
        int margin=Math.round(getResources().getDisplayMetrics().widthPixels/1920f*48);
        int y=getScrollY();
        if(rect.top<y+margin)goTo(rect.top-margin);
        else if(rect.bottom>y+getHeight()-margin)goTo(rect.bottom-getHeight()+margin);
    }
    void goTo(int y) {
        int max=getChildCount()==0?0:Math.max(0,getChildAt(0).getHeight()-getHeight());
        int target=Math.max(0,Math.min(max,y));
        stop();if(target==getScrollY())return;
        motion=ValueAnimator.ofInt(getScrollY(),target);motion.setDuration(300);
        motion.setInterpolator(new PathInterpolator(.2f,0f,0f,1f));
        motion.addUpdateListener(a->scrollTo(0,(Integer)a.getAnimatedValue()));motion.start();
    }
    void stop(){if(motion!=null){motion.cancel();motion=null;}removeCallbacks(reveal);}
    @Override protected void onDetachedFromWindow(){stop();super.onDetachedFromWindow();}
}

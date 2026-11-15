package io.freemytv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Cinema Android layout. Uses the existing catalog, app launchers and recovery machinery. */
final class CinemaView extends FrameLayout implements Recs.Listener {
    private final Activity activity;
    private final Recs recs;
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final ExecutorService sceneryLoader = Executors.newSingleThreadExecutor();
    private final DailyScenery dailyScenery;
    private final List<HomeApps.Item> apps = new ArrayList<>(), others = new ArrayList<>();
    private final Map<ImageView, Recs.Pick> artwork = new HashMap<>();
    private final Map<ImageView, WatchNext.Item> watchImages = new HashMap<>();
    private final List<WatchNext.Item> watching = new ArrayList<>();
    private final WatchArt watchArt;
    private ContinueArtwork continueArtwork;
    private boolean titleArtEnabled;
    private static final String[] SCENES = {"sahyadri-dusk.png", "aurora.png", "desert.png"};
    private static final String[] SCENE_NAMES = {"Sahyadri afterglow", "Northern lights", "Desert dreams"};
    private int sceneIndex;
    private boolean backgroundLoading, watchLoading;
    private ImageView incomingWallpaper;
    private TextView sceneButton;
    private String sceneTitle = SCENE_NAMES[0], sceneCredit = "Generated landscape · Free My TV Cinema";
    private final Runnable rotate = () -> changeBackground(true);
    private final Runnable refresh = new Runnable() {
        public void run() {
            if (!running || destroyed) return;
            refreshWatching(); recs.refreshIfStale(); postDelayed(this, 60_000);
        }
    };
    private final String[] tabs = {"Home", "Movies", "Shows", "Apps"};
    private final List<TextView> tabViews = new ArrayList<>();
    private final List<LinearLayout> rails = new ArrayList<>();
    private final int bg = 0xff07131d, muted = 0xffb7c9d3, accent = 0xffffdfad;
    private final float scale;
    private CinemaScroll scroll;
    private LinearLayout body, nav;
    private ImageView heroImage;
    private ImageView wallpaper;
    private Bitmap landscape;
    private TextView heroTitle, heroMeta, heroAction, heroEyebrow;
    private Recs.Pick featured;
    private final List<Recs.Pick> featuredPicks=new ArrayList<>();
    private final List<TextView> featuredButtons=new ArrayList<>();
    private int featuredIndex;
    private FrameLayout trailerHost;
    private TrailerPlayer trailerPlayer;
    private TextView trailerToggle, trailerStatus;
    private boolean trailerAutoplay=true;
    private String trailerPickKey="";
    private final Runnable startTrailer=this::beginTrailer;
    private final Runnable trailerTimeout=()->{trailerPlayer.stop();if(trailerHost!=null)trailerHost.setVisibility(GONE);if(trailerStatus!=null)trailerStatus.setText("ARTWORK PREVIEW  ·  Trailer timed out");if(featuredActive())postDelayed(this.nextFeatured,12_000);};
    private final Runnable nextFeatured=()->{if(featuredActive()&&featuredPicks.size()>1)chooseFeatured((featuredIndex+1)%featuredPicks.size());};
    private final android.view.ViewTreeObserver.OnGlobalFocusChangeListener trailerFocus=(oldView,newView)->updateTrailerFocus();
    private HomeView legacy;
    private int selected;
    private boolean running, destroyed;
    private String catalog = "";
    private final Map<String, View> focusTargets = new HashMap<>();

    CinemaView(Activity a) {
        super(a); activity = a; scale = a.getResources().getDisplayMetrics().widthPixels / 1920f;
        recs = Recs.get(a); recs.setLight(true); setBackgroundColor(bg);
        watchArt = new WatchArt(a, this::updateWatchArt); watchArt.setScale(scale);
        dailyScenery = new DailyScenery(a);
        trailerPlayer=new TrailerPlayer(a);
        trailerAutoplay=a.getSharedPreferences("cinema_trailers",0).getBoolean("autoplay",true);
        titleArtEnabled=a.getSharedPreferences("cinema_artwork",0).getBoolean("enabled",false);
        continueArtwork=new ContinueArtwork(this::updateWatchArt);
        setClipChildren(false); build();
    }
    private int px(float n) { return Math.round(n * scale); }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(px(radius)); return d;
    }
    private GradientDrawable panel(int color, int radius, boolean focused) {
        GradientDrawable d=shape(color,radius); d.setStroke(px(focused?3:1),focused?accent:0x28ffffff); return d;
    }
    private TextView text(String value, int size, int color) {
        TextView t = new TextView(activity); t.setText(value); t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size)); t.setFontFeatureSettings("kern");
        t.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL)); return t;
    }
    private void focusable(View v, int radius) {
        v.setFocusable(true); v.setClickable(true); v.setBackground(panel(0xb51b3040, radius, false));
        v.setOnFocusChangeListener((view, focused) -> {
            GradientDrawable d = panel(focused ? 0xf02b4350 : 0xb51b3040, radius, focused);
            view.setBackground(d); lift(view,focused);
            view.setElevation(px(focused?12:2));
        });
    }
    private TextView button(String label, Runnable action) {
        TextView t = text(label, 25, Color.WHITE); t.setGravity(Gravity.CENTER);
        t.setPadding(px(28), px(12), px(28), px(12)); focusable(t, 32);
        t.setOnClickListener(v -> action.run()); return t;
    }
    private void settings() { activity.startActivity(new Intent(activity, SettingsActivity.class)); }
    private void build() {
        removeAllViews(); artwork.clear(); tabViews.clear();
        wallpaper=new ImageView(activity); wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if(landscape!=null)wallpaper.setImageBitmap(landscape);
        addView(wallpaper,new FrameLayout.LayoutParams(-1,-1));
        incomingWallpaper=new ImageView(activity); incomingWallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);
        incomingWallpaper.setAlpha(0); addView(incomingWallpaper,new FrameLayout.LayoutParams(-1,-1));
        View veil=new View(activity); veil.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x8007131d,0x4007131d,0xf507131d})); addView(veil,new FrameLayout.LayoutParams(-1,-1));
        View contrast=new View(activity);contrast.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0xa607131d,0x2807131d,0x0007131d}));addView(contrast,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout root = new LinearLayout(activity); root.setOrientation(LinearLayout.VERTICAL);
        addView(root, new FrameLayout.LayoutParams(-1, -1));
        nav = new LinearLayout(activity); nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(px(88), px(25), px(88), px(16)); nav.setBackgroundColor(0x1807131d);
        TextView mark = text("Open Theatre", 23, Color.WHITE); mark.setLetterSpacing(.15f);
        mark.setTypeface(Typeface.create("sans-serif-medium",0)); nav.addView(mark, new LinearLayout.LayoutParams(px(260), -2));
        for (int i=0;i<tabs.length;i++) {
            final int index=i; TextView t=button(tabs[i], () -> select(index));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(px(155), px(62)); p.leftMargin=px(18);
            nav.addView(t,p); tabViews.add(t);
            t.setOnFocusChangeListener((v,has)->{updateTabs();if(has&&scroll!=null)scroll.goTo(0);});
        }
        View spacer=new View(activity); nav.addView(spacer,new LinearLayout.LayoutParams(0,1,1));
        TextClock clock=new TextClock(activity); clock.setFormat12Hour("h:mm a"); clock.setFormat24Hour("HH:mm");
        clock.setTextColor(muted); clock.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(25)); nav.addView(clock);
        TextView settings=button("Settings",this::settings);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(px(156),px(62)); sp.leftMargin=px(32); nav.addView(settings,sp);
        root.addView(nav,new LinearLayout.LayoutParams(-1,px(116)));
        scroll=new CinemaScroll(activity); scroll.setClipToPadding(false); scroll.setVerticalScrollBarEnabled(false);
        body=new LinearLayout(activity); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(0,0,0,px(90));
        scroll.addView(body); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        populate(); tabViews.get(selected).requestFocus();
    }
    private void select(int index) { selected=index; scroll.stop(); scroll.scrollTo(0,0); populate(); body.setAlpha(.4f);body.setTranslationY(px(12));body.animate().alpha(1).translationY(0).setDuration(220).start(); }
    private void updateTabs(){
        for(int i=0;i<tabViews.size();i++){
            TextView t=tabViews.get(i); boolean active=i==selected;
            t.setTextColor(active?bg:t.hasFocus()?accent:0xffd5e1e7);
            t.setBackground(active?panel(t.hasFocus()?0xfffff4df:accent,32,t.hasFocus()):panel(t.hasFocus()?0xa02a4050:0x001b3040,32,t.hasFocus()));
        }
    }
    private void populate() {
        View focused=findFocus(); Object focusTag=focused==null?null:focused.getTag(); int oldY=scroll.getScrollY();
        stopTrailer();scroll.stop();scroll.rebuilding=true;body.removeAllViews(); artwork.clear(); watchImages.clear(); focusTargets.clear(); rails.clear(); sceneButton=null; updateTabs();
        if(selected!=3) hero();
        appRow("Your apps",apps);
        if(selected==0) watchingRow();
        if(selected==3) appRow("More apps",others);
        else {
            List<Recs.Pick> movies=recs.now("movie"), shows=recs.now("tv");
            if(selected==0||selected==1)pickRow("Discover movies · new & trending",movies);
            if(selected==0||selected==2)pickRow("Discover shows · new & trending",shows);
            if((selected==0&&movies.isEmpty()&&shows.isEmpty())||(selected==1&&movies.isEmpty())||(selected==2&&shows.isEmpty()))serviceRow();
        }
        updateArt(); updateWatchArt();
        if(focusTag instanceof String){View restore=focusTargets.get(focusTag);if(restore!=null)restore.requestFocus();}
        scroll.post(()->{scroll.scrollTo(0,oldY);scroll.rebuilding=false;updateTrailerFocus();});
    }
    private void remember(View view,String tag){view.setTag(tag);focusTargets.put(tag,view);}
    private void hero() {
        List<Recs.Pick> list=recs.now(selected==1?"movie":selected==2?"tv":null);
        featuredPicks.clear();featuredPicks.addAll(list.subList(0,Math.min(3,list.size())));
        featuredIndex=Math.min(featuredIndex,Math.max(0,featuredPicks.size()-1));
        featured=featuredPicks.isEmpty()?null:featuredPicks.get(featuredIndex);featuredButtons.clear();
        FrameLayout frame=new FrameLayout(activity);
        View stage=new View(activity);stage.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0xe807131d,0xb007131d,0xe0101c28}));
        FrameLayout.LayoutParams stageP=new FrameLayout.LayoutParams(-1,-1);stageP.leftMargin=px(64);stageP.rightMargin=px(64);stageP.bottomMargin=px(12);
        frame.addView(stage,stageP);
        heroImage=new ImageView(activity); heroImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        heroImage.setBackground(shape(0x201b3040,24));heroImage.setClipToOutline(true);
        FrameLayout.LayoutParams poster=new FrameLayout.LayoutParams(px(864),px(486),Gravity.RIGHT|Gravity.TOP);
        poster.rightMargin=px(96);poster.topMargin=px(24);
        if(featured!=null)frame.addView(heroImage,poster);
        trailerHost=new FrameLayout(activity);trailerHost.setClipToOutline(true);trailerHost.setBackground(shape(Color.BLACK,24));
        trailerHost.setVisibility(GONE);frame.addView(trailerHost,new FrameLayout.LayoutParams(poster));
        LinearLayout copy=new LinearLayout(activity); copy.setOrientation(LinearLayout.VERTICAL); copy.setGravity(Gravity.CENTER_VERTICAL);
        copy.setPadding(px(96),px(20),px(40),px(20));
        TextView eyebrow=text(featured==null?"SETTLE IN. PRESS PLAY.":"SPOTLIGHT  "+(featuredIndex+1)+" / "+featuredPicks.size()+"  ·  "+Recs.serviceName(featured),20,accent); eyebrow.setLetterSpacing(.18f); copy.addView(eyebrow);
        heroEyebrow=eyebrow;
        heroTitle=text(featured==null?"Make room for\na good story.":featured.title,64,Color.WHITE);
        heroTitle.setTypeface(Typeface.create("sans-serif-medium",0)); heroTitle.setMaxLines(2); heroTitle.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleP=new LinearLayout.LayoutParams(-1,-2); titleP.topMargin=px(12); copy.addView(heroTitle,titleP);
        heroMeta=text(featured==null?"A little escape. A new favourite. All in one place.":featured.meta,25,0xffd2dfe5); heroMeta.setMaxLines(2);
        LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,-2); mp.topMargin=px(20); copy.addView(heroMeta,mp);
        heroAction=button(featured==null?"Explore your apps  →":"▶  Open in "+Recs.serviceName(featured),()->{if(featured==null)showAllApps();else open(featured);});
        remember(heroAction,"hero"); heroAction.setTextColor(bg); heroAction.setBackground(panel(accent,32,false));
        heroAction.setOnFocusChangeListener((v,has)->{v.setBackground(panel(has?Color.WHITE:accent,32,has));v.animate().scaleX(has?1.03f:1).scaleY(has?1.03f:1).setDuration(130).start();});
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-2,px(60)); ap.topMargin=px(20); copy.addView(heroAction,ap);
        if(!featuredPicks.isEmpty()){
            LinearLayout choices=new LinearLayout(activity);
            for(int i=0;i<featuredPicks.size();i++){
                final int index=i;String name=featuredPicks.get(i).title;
                TextView chip=button((i+1)+"  "+name,()->chooseFeatured(index));chip.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(18));
                chip.setSingleLine();chip.setEllipsize(TextUtils.TruncateAt.END);remember(chip,"featured:"+i);
                LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(px(215),px(54));cp.rightMargin=px(12);choices.addView(chip,cp);featuredButtons.add(chip);chip.setTextColor(i==featuredIndex?accent:Color.WHITE);
            }
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,-2);cp.topMargin=px(18);copy.addView(choices,cp);
            trailerToggle=button(trailerAutoplay?"Trailers: sound on  •  Pause":"Trailers paused  •  Play",()->{
                trailerAutoplay=!trailerAutoplay;activity.getSharedPreferences("cinema_trailers",0).edit().putBoolean("autoplay",trailerAutoplay).apply();
                trailerToggle.setText(trailerAutoplay?"Trailers: sound on  •  Pause":"Trailers paused  •  Play");updateTrailerFocus();});
            trailerToggle.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(18));remember(trailerToggle,"trailers");
            LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-2,px(44));tp.topMargin=px(10);copy.addView(trailerToggle,tp);
        }
        frame.addView(copy,new FrameLayout.LayoutParams(px(900),px(520)));
        trailerStatus=text("TRAILER PREVIEW  ·  YouTube",17,muted);trailerStatus.setLetterSpacing(.08f);
        FrameLayout.LayoutParams statusP=new FrameLayout.LayoutParams(px(864),px(32),Gravity.RIGHT|Gravity.TOP);statusP.rightMargin=px(96);statusP.topMargin=px(520);frame.addView(trailerStatus,statusP);
        {
            LinearLayout caption=new LinearLayout(activity);caption.setOrientation(LinearLayout.VERTICAL);caption.setGravity(Gravity.RIGHT);
            sceneButton=button(sceneTitle+"  ↻",()->changeBackground(true));
            sceneButton.setMaxWidth(px(640));sceneButton.setSingleLine();sceneButton.setEllipsize(TextUtils.TruncateAt.END);
            sceneButton.setContentDescription("Next background. Hold OK for photo credits.");
            sceneButton.setOnLongClickListener(v->{new android.app.AlertDialog.Builder(activity).setTitle(sceneTitle)
                    .setMessage(sceneCredit+"\n\nFresh scenic photos are checked daily while home is open. Backgrounds change every five minutes. Downloaded photos remain available offline.")
                    .setPositiveButton("OK",null).show();return true;});
            sceneButton.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(16)); remember(sceneButton,"background");caption.addView(sceneButton);
            TextView place=text("New daily · Every 5 min · Hold OK for credits",14,0xffc8d9e0);place.setPadding(0,px(10),0,0);caption.addView(place);
            FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-2,-2,Gravity.RIGHT|Gravity.BOTTOM);cp.rightMargin=px(96);cp.bottomMargin=px(24);frame.addView(caption,cp);
        }
        body.addView(frame,new LinearLayout.LayoutParams(-1,px(620)));
        if(featured!=null) artwork.put(heroImage,featured);
    }
    private boolean featuredActive(){
        if(!running||destroyed||selected==3||featured==null||legacy!=null)return false;
        View focus=findFocus();return focus==heroAction||focus==trailerToggle||featuredButtons.contains(focus)||tabViews.contains(focus);
    }
    private void stopTrailer(){
        removeCallbacks(startTrailer);removeCallbacks(nextFeatured);removeCallbacks(trailerTimeout);trailerPickKey="";
        if(trailerPlayer!=null)trailerPlayer.stop();if(trailerHost!=null){trailerHost.animate().cancel();trailerHost.setVisibility(GONE);}if(trailerStatus!=null)trailerStatus.setText(trailerAutoplay?"TRAILER PREVIEW  ·  YouTube":"TRAILERS PAUSED");
    }
    private void updateTrailerFocus(){
        removeCallbacks(startTrailer);
        if(!featuredActive()||!trailerAutoplay){stopTrailer();return;}
        if(trailerPickKey.isEmpty())postDelayed(startTrailer,1800);
    }
    private void chooseFeatured(int index){
        if(index<0||index>=featuredPicks.size())return;stopTrailer();featuredIndex=index;featured=featuredPicks.get(index);
        heroTitle.setText(featured.title);heroMeta.setText(featured.meta);heroEyebrow.setText("SPOTLIGHT  "+(index+1)+" / "+featuredPicks.size()+"  ·  "+Recs.serviceName(featured));
        heroAction.setText("▶  Open in "+Recs.serviceName(featured));heroImage.setImageDrawable(null);artwork.put(heroImage,featured);updateArt();
        for(int i=0;i<featuredButtons.size();i++)featuredButtons.get(i).setTextColor(i==index?accent:Color.WHITE);
        updateTrailerFocus();
    }
    private void beginTrailer(){
        if(!featuredActive()||!trailerAutoplay||!trailerPickKey.isEmpty())return;
        trailerPickKey=featured.kind+featured.id;trailerHost.setAlpha(0f);trailerHost.setVisibility(VISIBLE);trailerStatus.setText("LOADING TRAILER  ·  YouTube");
        trailerPlayer.play(featured,trailerHost,()->{
            trailerHost.animate().alpha(1f).setDuration(450).start();trailerStatus.setText("NOW PREVIEWING  ·  YouTube  ·  Sound on");removeCallbacks(trailerTimeout);removeCallbacks(nextFeatured);if(featuredActive())postDelayed(nextFeatured,90_000);
        },()->{removeCallbacks(trailerTimeout);removeCallbacks(nextFeatured);trailerHost.setVisibility(GONE);trailerStatus.setText("UP NEXT  ·  Another story awaits");if(featuredActive())postDelayed(nextFeatured,1200);},()->{
            removeCallbacks(trailerTimeout);trailerHost.setVisibility(GONE);trailerStatus.setText("ARTWORK PREVIEW  ·  Trailer unavailable");removeCallbacks(nextFeatured);if(featuredActive())postDelayed(nextFeatured,12_000);
        });
        postDelayed(trailerTimeout,25_000);
    }
    private LinearLayout rail(String label) {
        TextView heading=text(label,28,Color.WHITE); heading.setTypeface(Typeface.create("sans-serif-medium",0));
        heading.setPadding(px(96),px(18),px(96),px(8)); body.addView(heading);
        HorizontalScrollView h=new HorizontalScrollView(activity); h.setHorizontalScrollBarEnabled(false); h.setClipToPadding(false);
        h.setOverScrollMode(OVER_SCROLL_NEVER);h.setPadding(px(88),px(16),px(88),px(24)); h.setClipChildren(false);
        LinearLayout row=new LinearLayout(activity); row.setClipChildren(false); row.setPadding(px(8),px(8),px(8),px(8)); h.addView(row);
        rails.add(row);
        body.addView(h); return row;
    }
    private void appRow(String label,List<HomeApps.Item> list) {
        LinearLayout row=rail(label);
        if(list.isEmpty()) { TextView t=text("Loading your apps…",24,muted); row.addView(t); return; }
        for(HomeApps.Item item:list) {
            LinearLayout card=new LinearLayout(activity); card.setOrientation(LinearLayout.VERTICAL); card.setGravity(Gravity.CENTER);
            card.setPadding(px(18),px(12),px(18),px(10)); focusable(card,40); card.setContentDescription(item.label);remember(card,"app:"+item.pkg);
            ImageView logo=new ImageView(activity); logo.setScaleType(ImageView.ScaleType.FIT_CENTER); logo.setImageBitmap(item.logo!=null?item.logo:item.tile);
            card.addView(logo,new LinearLayout.LayoutParams(px(148),px(60)));
            TextView name=text(item.label,20,Color.WHITE); name.setGravity(Gravity.CENTER); name.setSingleLine(); name.setEllipsize(TextUtils.TruncateAt.END);
            card.addView(name,new LinearLayout.LayoutParams(-1,px(34)));
            card.setOnClickListener(v->HomeApps.open(activity,item,null));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(px(218),px(128)); cp.rightMargin=px(22); row.addView(card,cp);
        }
    }
    private void serviceRow(){
        LinearLayout row=rail("Find your next favourite");
        int shown=0;
        for(HomeApps.Item item:apps){
            if(shown==3)break;
            if(item.pkg==null||item.pkg.contains("spotify"))continue;
            final int index=shown++;
            int[][] colors={{0xe5442338,0xe51f172c},{0xe51a4559,0xe511283c},{0xe54b3b28,0xe51b2e31}};
            FrameLayout card=new FrameLayout(activity);focusable(card,24);remember(card,"service:"+item.pkg);
            GradientDrawable fill=new GradientDrawable(GradientDrawable.Orientation.TL_BR,colors[index]);fill.setCornerRadius(px(24));fill.setStroke(px(1),0x35ffffff);card.setBackground(fill);
            card.setOnFocusChangeListener((v,has)->{GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,colors[index]);d.setCornerRadius(px(24));d.setStroke(px(has?3:1),has?accent:0x35ffffff);v.setBackground(d);v.animate().scaleX(has?1.025f:1).scaleY(has?1.025f:1).setDuration(130).start();});
            ImageView logo=new ImageView(activity);logo.setImageBitmap(item.logo!=null?item.logo:item.tile);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);logo.setAlpha(.8f);
            FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(px(150),px(70),Gravity.RIGHT|Gravity.TOP);lp.rightMargin=px(26);lp.topMargin=px(28);card.addView(logo,lp);
            LinearLayout content=new LinearLayout(activity);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(px(30),px(28),px(30),px(25));
            TextView label=text("EXPLORE",16,accent);label.setLetterSpacing(.15f);content.addView(label);
            TextView title=text(item.label,32,Color.WHITE);title.setTypeface(Typeface.create("sans-serif-medium",0));title.setPadding(0,px(48),0,px(12));content.addView(title);
            content.addView(text("Open app  ↗",21,0xffd3e1e6));card.addView(content,new FrameLayout.LayoutParams(-1,-1));
            card.setContentDescription("Open "+item.label);card.setOnClickListener(v->HomeApps.open(activity,item,null));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(px(550),px(222));cp.rightMargin=px(26);row.addView(card,cp);
        }
        TextView setup=button(Recs.hasKey(activity)?"Manage your movie recommendations  →":"Personalise your movie picks  →",this::settings);
        remember(setup,"recommendations");LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,px(58));p.leftMargin=px(96);p.topMargin=px(18);body.addView(setup,p);
    }
    private void open(Recs.Pick p) { if(p.pkg!=null) HomeApps.open(activity,HomeApps.app(p.pkg,Recs.serviceName(p),p.link),null); }
    private void pickRow(String label,List<Recs.Pick> list) {
        LinearLayout row=rail(label);
        if(list.isEmpty()) {
            LinearLayout empty=new LinearLayout(activity); empty.setOrientation(LinearLayout.VERTICAL); empty.setPadding(px(28),px(20),px(28),px(20));
            focusable(empty,18); empty.addView(text(Recs.hasKey(activity)?"Your recommendations are on their way":"Discover what to watch next",27,Color.WHITE));
            TextView hint=text(Recs.hasKey(activity)?"Check Picks in Home Settings if no titles appear.":"Add your free TMDB key in Home Settings to see movie artwork and recommendations.",22,muted);
            hint.setPadding(0,px(10),0,0); empty.addView(hint); empty.setOnClickListener(v->settings()); row.addView(empty,new LinearLayout.LayoutParams(px(1700),px(134))); return;
        }
        for(Recs.Pick pick:list) {
            FrameLayout card=mediaCard(pick.title,Recs.serviceName(pick),-1);
            ImageView art=(ImageView)card.getChildAt(0);artwork.put(art,pick);
            card.setContentDescription(pick.title+", "+Recs.serviceName(pick));card.setOnClickListener(v->open(pick));
            remember(card,"pick:"+pick.kind+pick.id);
            addMedia(row,card);
        }
    }
    private void lift(View view,boolean focused){
        view.animate().cancel();view.animate().scaleX(focused?1.045f:1).scaleY(focused?1.045f:1)
            .translationY(px(focused?-4:0)).setDuration(190)
            .setInterpolator(new android.view.animation.PathInterpolator(.2f,0f,0f,1f)).start();
        view.setElevation(px(focused?18:3));
    }
    private FrameLayout mediaCard(String title,String detail,float progress){
        FrameLayout card=new FrameLayout(activity);card.setFocusable(true);card.setClickable(true);
        card.setBackground(shape(0xff15232e,28));card.setClipToOutline(true);
        ImageView art=new ImageView(activity);art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(art,new FrameLayout.LayoutParams(-1,-1));
        View gradient=new View(activity);gradient.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{0x0007131d,0x1807131d,0xf507131d}));card.addView(gradient,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout copy=new LinearLayout(activity);copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(px(22),px(12),px(22),px(progress>=0?28:19));
        TextView name=text(title,25,Color.WHITE);name.setTypeface(Typeface.create("sans-serif-medium",0));
        name.setSingleLine();name.setEllipsize(TextUtils.TruncateAt.END);copy.addView(name);
        TextView sub=text(detail,18,0xffd0dce3);sub.setPadding(0,px(5),0,0);sub.setSingleLine();sub.setEllipsize(TextUtils.TruncateAt.END);copy.addView(sub);
        card.addView(copy,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        if(progress>=0){
            ProgressBar bar=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);
            bar.setMax(1000);bar.setProgress(Math.round(progress*1000));bar.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));
            bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0x50ffffff));
            FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(-1,px(4),Gravity.BOTTOM);bp.leftMargin=px(22);bp.rightMargin=px(22);bp.bottomMargin=px(13);card.addView(bar,bp);
        }
        card.setForeground(panel(Color.TRANSPARENT,28,false));
        card.setOnFocusChangeListener((v,has)->{v.setForeground(panel(Color.TRANSPARENT,28,has));lift(v,has);});
        return card;
    }
    private void addMedia(LinearLayout row,FrameLayout card){
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(px(372),px(238));cp.rightMargin=px(28);row.addView(card,cp);
    }

    private void updateArt() {
        for(Map.Entry<ImageView,Recs.Pick> entry:artwork.entrySet()) {
            Backdrop b=recs.backdrop(entry.getValue()); if(b!=null) entry.getKey().setImageBitmap(b.wide!=null?b.wide:b.image);
        }
    }
    private void watchingRow() {
        LinearLayout row=rail("Continue watching");
        if(!titleArtEnabled){
            TextView enable=button("Show artwork",this::artworkSettings);
            remember(enable,"watch-artwork");
            LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(px(220),px(272));ep.rightMargin=px(24);row.addView(enable,ep);
        }
        if(watching.isEmpty()) {
            TextView empty=text(WatchNext.allowed(activity)
                    ? "Your last six shows and movies appear here when apps share them with your TV."
                    : "Enable Continue Watching in Home Settings to see your last six titles.",23,muted);
            empty.setPadding(px(16),px(16),px(16),px(16)); row.addView(empty); return;
        }
        for(WatchNext.Item item:watching) {
            String detail=(item.episode==null?"":item.episode+" · ")+item.app;
            FrameLayout card=mediaCard(item.title,detail,item.progress());
            watchImages.put((ImageView)card.getChildAt(0),item);
            card.setContentDescription("Resume "+item.title+", "+detail);
            remember(card,"watch:"+item.pkg+":"+item.title);card.setOnClickListener(v->resumeTitle(item));
            card.setOnLongClickListener(v->{artworkSettings();return true;});addMedia(row,card);
        }
    }

    private void resumeTitle(WatchNext.Item item) {
        Intent link=item.intent==null?null:HomeView.linkIntent(item.intent,item.pkg);
        try {
            if(link!=null&&link.resolveActivity(activity.getPackageManager())!=null) {
                activity.startActivity(link.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return;
            }
        } catch(android.content.ActivityNotFoundException | SecurityException e) { AppLog.w("Resume link unavailable"); }
        Toast.makeText(activity,"Opening "+item.app+" — this app did not provide a usable resume link.",Toast.LENGTH_LONG).show();
        HomeApps.open(activity,HomeApps.app(item.pkg,item.app),null);
    }
    private void updateWatchArt() {
        if(destroyed)return;
        for(Map.Entry<ImageView,WatchNext.Item> entry:watchImages.entrySet()) {
            WatchNext.Item it=entry.getValue();
            Bitmap b=titleArtEnabled?continueArtwork.get(it):null;
            if(b==null)b=watchArt.get(it.pictureUri,it.pictureKey,it.bannerKey,it.pkg);
            if(b!=null)entry.getKey().setImageBitmap(b);
        }
    }
    private void artworkSettings(){
        new android.app.AlertDialog.Builder(activity).setTitle("Continue Watching artwork")
            .setMessage(titleArtEnabled?"Matching show and movie pictures are enabled. Turn off online artwork?"
                :"Find show and movie pictures using TMDB? This sends the title only, without your account details or playback progress.")
            .setPositiveButton(titleArtEnabled?"Turn off":"Enable",(dialog,which)->{
                titleArtEnabled=!titleArtEnabled;activity.getSharedPreferences("cinema_artwork",0).edit().putBoolean("enabled",titleArtEnabled).apply();
                continueArtwork.close();continueArtwork=new ContinueArtwork(this::updateWatchArt);populate();
                if(!watching.isEmpty()){View first=focusTargets.get("watch:"+watching.get(0).pkg+":"+watching.get(0).title);if(first!=null)first.requestFocus();}
            }).setNegativeButton("Cancel",null).show();
    }
    private String watchSignature(List<WatchNext.Item> list) {
        StringBuilder s=new StringBuilder();
        for(WatchNext.Item it:list)s.append(it.pkg).append('|').append(it.title).append('|').append(it.intent)
                .append('|').append(it.position).append('|').append(it.duration).append('|').append(it.picture()).append('\n');
        return s.toString();
    }
    private void refreshWatching() {
        if(watchLoading||destroyed)return;watchLoading=true;
        loader.execute(()->{
            List<WatchNext.Item> latest=WatchNext.loadCinema(activity);
            post(()->{watchLoading=false;if(destroyed||!running)return;
                if(!watchSignature(latest).equals(watchSignature(watching))){watching.clear();watching.addAll(latest);if(selected==0)populate();}
            });
        });
    }
    private void changeBackground(boolean advance) {
        removeCallbacks(rotate);
        if(!running||destroyed||backgroundLoading)return;
        backgroundLoading=true;final int next=advance?(sceneIndex+1)%SCENES.length:sceneIndex;
        final boolean showFallbackFirst=landscape==null;
        sceneryLoader.execute(()->{
            if(showFallbackFirst){
                Bitmap fallback=bundledScene(next);
                post(()->{if(!destroyed&&running&&landscape==null&&fallback!=null){landscape=fallback;wallpaper.setImageBitmap(fallback);}});
            }
            DailyScenery.Scene online=dailyScenery.next(advance);
            Bitmap decoded=null;
            if(online!=null)decoded=online.bitmap;
            else decoded=bundledScene(next);
            final Bitmap image=decoded;
            post(()->{
                backgroundLoading=false;if(destroyed||!running)return;
                if(image!=null){
                    incomingWallpaper.animate().cancel();sceneIndex=next;
                    sceneTitle=online==null?SCENE_NAMES[next]:online.title;
                    sceneCredit=online==null?"Generated landscape · Free My TV Cinema":online.credit;
                    if(landscape==null){landscape=image;wallpaper.setImageBitmap(image);}
                    else {
                        incomingWallpaper.setImageBitmap(image);incomingWallpaper.setAlpha(0);
                        incomingWallpaper.animate().alpha(1).setDuration(1600).withEndAction(()->{
                            landscape=image;wallpaper.setImageBitmap(image);incomingWallpaper.setAlpha(0);incomingWallpaper.setImageDrawable(null);
                        }).start();
                    }
                    if(sceneButton!=null)sceneButton.setText(sceneTitle+"  ↻");
                }
                postDelayed(rotate,300_000);
            });
        });
    }
    private Bitmap bundledScene(int index) {
        try(java.io.InputStream in=activity.getAssets().open("cinema/"+SCENES[index])){return BitmapFactory.decodeStream(in);}
        catch(java.io.IOException | OutOfMemoryError e){AppLog.w("Cinema background unavailable");return null;}
    }
    @Override public void onRecs() {
        if(!running || destroyed)return;
        StringBuilder ids=new StringBuilder();for(String kind:new String[]{"movie","tv"})for(Recs.Pick p:recs.now(kind))ids.append(p.kind).append(p.id).append(p.title);
        String next=ids.toString(); if(!catalog.equals(next)){catalog=next;populate();}else updateArt();
    }
    void resume() {
        if(running)return; running=true;getViewTreeObserver().addOnGlobalFocusChangeListener(trailerFocus);updateTrailerFocus(); recs.setLight(true); recs.addListener(this); recs.refreshIfStale();
        changeBackground(false);removeCallbacks(refresh);refresh.run();
        loader.execute(()->{
            List<HomeApps.Item> loaded=HomeApps.shelf(activity,true), extra=HomeApps.others(activity);
            Set<String> added=new HashSet<>();for(HomeApps.Item it:loaded)added.add(it.pkg);
            for(HomeApps.Item it:extra)if(loaded.size()<8&&it.pkg!=null&&(it.pkg.contains("hotstar")||it.pkg.contains("sonyliv")||it.pkg.contains("zee")||it.pkg.contains("discovery"))&&added.add(it.pkg))loaded.add(it);
            List<HomeApps.Item> remaining=new ArrayList<>();for(HomeApps.Item it:extra)if(!added.contains(it.pkg))remaining.add(it);
            post(()->{if(destroyed||!running)return;apps.clear();apps.addAll(loaded);others.clear();others.addAll(remaining);populate();});
        });
        if(legacy!=null)legacy.resume();
    }
    void pause(){getViewTreeObserver().removeOnGlobalFocusChangeListener(trailerFocus);stopTrailer();scroll.stop();running=false;removeCallbacks(rotate);removeCallbacks(refresh);recs.removeListener(this);if(legacy!=null)legacy.pause();}
    void destroy(){destroyed=true;pause();incomingWallpaper.animate().cancel();watchArt.close();continueArtwork.close();trailerPlayer.destroy();loader.shutdownNow();sceneryLoader.shutdownNow();if(legacy!=null)legacy.destroy();}
    void reset(boolean animated){closeLegacy();select(0);tabViews.get(0).requestFocus();}
    void showAllApps(){closeLegacy();select(3);tabViews.get(3).requestFocus();}
    void showRecents(){if(legacy==null){legacy=new HomeView(activity);addView(legacy,new FrameLayout.LayoutParams(-1,-1));legacy.resume();}legacy.showRecents();}
    private void closeLegacy(){if(legacy!=null){legacy.destroy();removeView(legacy);legacy=null;recs.setLight(true);}}
    boolean onKey(KeyEvent e){
        if(legacy!=null){if(e.getKeyCode()==KeyEvent.KEYCODE_BACK&&e.getAction()==KeyEvent.ACTION_UP){closeLegacy();return true;}return legacy.onKey(e);}
        if(e.getKeyCode()==KeyEvent.KEYCODE_BACK){if(e.getAction()==KeyEvent.ACTION_UP)reset(true);return true;}
        if(e.getKeyCode()==KeyEvent.KEYCODE_DPAD_DOWN||e.getKeyCode()==KeyEvent.KEYCODE_DPAD_UP){
            View focus=findFocus();int direction=e.getKeyCode()==KeyEvent.KEYCODE_DPAD_DOWN?1:-1;
            for(int r=0;r<rails.size();r++){
                LinearLayout row=rails.get(r);int column=row.indexOfChild(focus);if(column<0)continue;
                if(e.getAction()==KeyEvent.ACTION_DOWN){
                    boolean moved=false;
                    for(int next=r+direction;next>=0&&next<rails.size();next+=direction){
                        LinearLayout candidate=rails.get(next);List<View> targets=new ArrayList<>();
                        for(int i=0;i<candidate.getChildCount();i++)if(candidate.getChildAt(i).isFocusable())targets.add(candidate.getChildAt(i));
                        if(!targets.isEmpty()){targets.get(Math.min(column,targets.size()-1)).requestFocus();moved=true;break;}
                    }
                    if(!moved&&direction<0){if(selected!=3)heroAction.requestFocus();else tabViews.get(selected).requestFocus();}
                }
                return true;
            }
        }
        return false;
    }
}

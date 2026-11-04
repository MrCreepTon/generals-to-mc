package local.generalsmc.client;

import icyllis.modernui.core.Context;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.drawable.*;
import icyllis.modernui.text.*;
import icyllis.modernui.util.*;
import icyllis.modernui.view.*;
import icyllis.modernui.widget.*;
import net.minecraft.client.gui.components.AbstractWidget;
import java.util.*;

/** Retained native controls; immutable snapshots cross the Minecraft/UI thread boundary. */
public final class ModernMenuFragment extends Fragment {
 private ModernMenuScreen host;private volatile Model latest;private volatile Root root;volatile int controlCount;
 record Item(AbstractWidget source,int x,int y,int width,int height,String text,String tooltip,boolean enabled,boolean focus,boolean edit,boolean primary,boolean danger,boolean left,int chip,boolean selected,boolean quiet,boolean choice){}
 record Style(boolean primary,boolean danger,boolean edit,boolean quiet){}
 record Model(int width,int height,List<Item> items){}
 public ModernMenuFragment(){}
 ModernMenuFragment(ModernMenuScreen host){this.host=host;}
 void update(){
  var list=new ArrayList<Item>();for(var child:host.backend.children())if(child instanceof AbstractWidget w&&w.visible){
   if(w instanceof LobbyCell cell&&cell.popover)continue;
   boolean edit=w instanceof net.minecraft.client.gui.components.EditBox;
   if(!(w instanceof net.minecraft.client.gui.components.Button)&&!edit)continue;
   var b=w instanceof MenuButton button?button:null;String label=edit?((net.minecraft.client.gui.components.EditBox)w).getValue():w.getMessage().getString();
   boolean selected=b!=null&&b.selected;if(selected&&!edit)label="✓ "+label;
   list.add(new Item(w,w.getX(),w.getY(),w.getWidth(),w.getHeight(),label,host.tooltip(w),w.active,w.isFocused(),edit,b!=null&&b.kind==MenuButton.Kind.PRIMARY,b!=null&&b.kind==MenuButton.Kind.DANGER,b!=null&&(b.leftAligned||b.kind==MenuButton.Kind.FIELD),w instanceof LobbyCell c?c.chip:-1,selected,b!=null&&b.kind==MenuButton.Kind.QUIET,w.active&&b!=null&&(b.disclosure||b instanceof LobbyCell c&&c.dropdown)));
  }
  var next=new Model(host.backend.width,host.backend.height,List.copyOf(list));if(next.equals(latest))return;latest=next;var r=root;if(r!=null)r.post(()->{if(!host.closed)r.apply(latest);});
 }
 @Override public View onCreateView(LayoutInflater inflater,ViewGroup container,DataSet saved){try{if(host==null){host=ModernMenuScreen.presented.get();if(host==null)throw new IllegalStateException("Menu host is no longer attached");latest=host.fragment.latest;}var r=new Root(requireContext());root=r;r.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));r.apply(latest);return r;}catch(RuntimeException|LinkageError error){if(host!=null)host.fail(error);else MenuScreens.unavailable(error);return new FrameLayout(requireContext());}}
 @Override public void onDestroyView(){root=null;super.onDestroyView();}
 final class Root extends FrameLayout {
  final IdentityHashMap<AbstractWidget,View> controls=new IdentityHashMap<>();final IdentityHashMap<AbstractWidget,Style> styles=new IdentityHashMap<>();final IdentityHashMap<AbstractWidget,String> pendingEdits=new IdentityHashMap<>();final IdentityHashMap<AbstractWidget,View> chips=new IdentityHashMap<>();final IdentityHashMap<AbstractWidget,TextView> carets=new IdentityHashMap<>();Model model;boolean syncing;double lastScale;
  Root(Context context){super(context);}
  void apply(Model next){if(next==null)return;syncing=true;try{
   var wanted=Collections.newSetFromMap(new IdentityHashMap<AbstractWidget,Boolean>());for(var item:next.items)wanted.add(item.source);
   for(var source:new ArrayList<>(controls.keySet()))if(!wanted.contains(source)){removeView(controls.remove(source));styles.remove(source);pendingEdits.remove(source);var chip=chips.remove(source);if(chip!=null)removeView(chip);var caret=carets.remove(source);if(caret!=null)removeView(caret);}
   for(var item:next.items){View view=controls.get(item.source);if(view==null){view=create(item);controls.put(item.source,view);addView(view,new FrameLayout.LayoutParams(1,1));if(item.chip!=-1){var chip=new TextView(getContext());chip.setText("●");chip.setIncludeFontPadding(false);chip.setGravity(Gravity.CENTER);chip.setTextColor(0xff000000|item.chip);chip.setEnabled(false);chips.put(item.source,chip);addView(chip,new FrameLayout.LayoutParams(1,1));}}
    if(item.choice&&!carets.containsKey(item.source)){var caret=new TextView(getContext());caret.setText("▾");caret.setIncludeFontPadding(false);caret.setGravity(Gravity.CENTER);caret.setTextColor(UiPaint.MUTED);caret.setEnabled(false);carets.put(item.source,caret);addView(caret,new FrameLayout.LayoutParams(1,1));}else if(!item.choice){var caret=carets.remove(item.source);if(caret!=null)removeView(caret);}
    view.setEnabled(item.enabled);view.setSelected(item.selected);view.setTooltipText(item.tooltip.isBlank()?null:item.tooltip);
    var style=new Style(item.primary,item.danger,item.edit,item.quiet);if(!style.equals(styles.get(item.source))){view.setBackground(background(style));styles.put(item.source,style);}
    var text=(TextView)view;if(!pendingEdits.containsKey(item.source)&&!text.getText().toString().equals(item.text))text.setText(item.text);
    text.setTextColor(item.selected?UiPaint.ACCENT:item.enabled?(item.primary?0xff111b25:UiPaint.TEXT):UiPaint.MUTED);
    if(item.focus&&!view.isFocused())view.requestFocus();
   }model=next;controlCount=controls.size();requestLayout();
  }finally{syncing=false;}}
  View create(Item item){
   TextView view;if(item.edit){var input=new EditText(getContext());input.setSingleLine(true);input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){if(!syncing){String value=s.toString();pendingEdits.put(item.source,value);host.act(item.source,()->{var source=(net.minecraft.client.gui.components.EditBox)item.source;source.setValue(value);String accepted=source.getValue();post(()->{if(Objects.equals(pendingEdits.get(item.source),value)){pendingEdits.remove(item.source);var target=(TextView)controls.get(item.source);if(target!=null&&!target.getText().toString().equals(accepted)){syncing=true;try{target.setText(accepted);}finally{syncing=false;}}}});});}}public void afterTextChanged(Editable value){}});input.setOnKeyListener((v,key,event)->{if(key==KeyEvent.KEY_ENTER&&event.getAction()==KeyEvent.ACTION_DOWN){host.act(item.source,()->host.keyBackend(257,0,0));return true;}return false;});view=input;
   }else{var button=new Button(getContext());button.setOnClickListener(v->host.act(item.source,()->host.pressBackend((net.minecraft.client.gui.components.Button)item.source)));view=button;}
   view.setGravity(Gravity.CENTER_VERTICAL|(item.left||item.edit?Gravity.LEFT:Gravity.CENTER_HORIZONTAL));view.setMaxLines(1);view.setEllipsize(TextUtils.TruncateAt.END);
   view.setOnFocusChangeListener((v,focused)->{if(focused)host.act(item.source,()->host.backend.setFocused(item.source));});
   return view;
  }
  ShapeDrawable shape(int fill,int stroke,int width){var shape=new ShapeDrawable();shape.setColor(fill);shape.setCornerRadius(dp(5));shape.setStroke(dp(width),stroke);return shape;}
  Drawable background(Style style){
   int fill=style.primary?0xffd6a85f:style.danger?0xff2c2028:style.edit?0xff0e1720:style.quiet?0x001b2835:UiPaint.SURFACE;
   int edge=style.primary?0xfff4d59e:style.danger?0xff70404a:style.quiet?0:UiPaint.BORDER;
   var states=new StateListDrawable();
   states.addState(new int[]{icyllis.modernui.R.attr.state_enabled,icyllis.modernui.R.attr.state_focused},shape(fill,UiPaint.FOCUS,2));
   states.addState(new int[]{icyllis.modernui.R.attr.state_selected},shape(0xff243e4d,UiPaint.BLUE,1));
   states.addState(new int[]{-icyllis.modernui.R.attr.state_enabled},shape(0xff17212c,0xff2b3642,1));
   states.addState(new int[]{icyllis.modernui.R.attr.state_hovered},shape(style.primary?0xfff0cf94:0xff2b4051,0xff6d8b9e,1));
   states.addState(StateSet.WILD_CARD,shape(fill,edge,1));
   var mask=new ShapeDrawable();mask.setColor(0xffffffff);mask.setCornerRadius(dp(5));
   return new RippleDrawable(ColorStateList.valueOf(style.primary?0x40ffffff:0x40d4a75d),states,mask);
  }
  @Override protected void onMeasure(int widthSpec,int heightSpec){int w=MeasureSpec.getSize(widthSpec),h=MeasureSpec.getSize(heightSpec);setMeasuredDimension(w,h);if(model==null)return;double sx=(double)w/Math.max(1,model.width),sy=(double)h/Math.max(1,model.height);for(var item:model.items){var v=controls.get(item.source);v.measure(MeasureSpec.makeMeasureSpec(Math.max(1,(int)Math.round(item.width*sx)),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.max(1,(int)Math.round(item.height*sy)),MeasureSpec.EXACTLY));var chip=chips.get(item.source);if(chip!=null)chip.measure(MeasureSpec.makeMeasureSpec((int)Math.round(9*sx),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec((int)Math.round(9*sy),MeasureSpec.EXACTLY));var caret=carets.get(item.source);if(caret!=null)caret.measure(MeasureSpec.makeMeasureSpec((int)Math.round(9*sx),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec((int)Math.round(9*sy),MeasureSpec.EXACTLY));}}
  @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){if(model==null)return;double sx=(double)(right-left)/Math.max(1,model.width),sy=(double)(bottom-top)/Math.max(1,model.height);for(var item:model.items){var v=(TextView)controls.get(item.source);int x=(int)Math.round(item.x*sx),y=(int)Math.round(item.y*sy);v.layout(x,y,x+v.getMeasuredWidth(),y+v.getMeasuredHeight());var chip=chips.get(item.source);if(chip!=null){int cx=x+(int)Math.round(8*sx),cy=y+(v.getMeasuredHeight()-chip.getMeasuredHeight())/2;((TextView)chip).setTextSize(icyllis.modernui.resources.TypedValue.COMPLEX_UNIT_PX,(float)(7*sy));chip.layout(cx,cy,cx+chip.getMeasuredWidth(),cy+chip.getMeasuredHeight());}var caret=carets.get(item.source);if(caret!=null){int cx=x+v.getMeasuredWidth()-(int)Math.round(16*sx),cy=y+(v.getMeasuredHeight()-caret.getMeasuredHeight())/2;caret.setTextSize(icyllis.modernui.resources.TypedValue.COMPLEX_UNIT_PX,(float)(9*sy));caret.layout(cx,cy,cx+caret.getMeasuredWidth(),cy+caret.getMeasuredHeight());}if(Math.abs(v.getTextSize()-9*sy)>.01){v.setTextSize(icyllis.modernui.resources.TypedValue.COMPLEX_UNIT_PX,(float)(9*sy));}v.setPadding((int)Math.round((item.chip==-1?9:24)*sx),0,(int)Math.round((item.choice?23:9)*sx),0);}lastScale=sy;}
 }
}

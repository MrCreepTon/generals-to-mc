package local.generalsmc.client;
/** Virtual pointer inside the owned game; never moves the operating-system mouse. */
final class NativeResumeHoverBench extends NativeWorldScreen {
 private final int actor;private int frames,hoverX=-1,hoverY;
 NativeResumeHoverBench(local.generalsmc.nativebridge.NativeSession session,int actor){super(session);this.actor=actor;}
 @Override public void render(net.minecraft.client.gui.GuiGraphics g,int x,int y,float partial){
  var v=NativeWorldView.current;if(v==null)return;x=width/2;y=height/3;
  if(actor!=0){var a=v.actor(actor);if(a!=null&&++frames>100){if(hoverX<0){var p=project(v.origin.world(a.x(),a.y(),a.z()+a.geometryHeight()*.5));hoverX=(int)p[0];hoverY=(int)p[1];}x=hoverX;y=hoverY;}}
  super.render(g,x,y,partial);
 }
}

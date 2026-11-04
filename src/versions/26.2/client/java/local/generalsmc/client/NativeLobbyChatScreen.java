package local.generalsmc.client;

import local.generalsmc.nativebridge.NativeNetworkServer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import java.util.*;

/** Room-only chat on the authenticated Minecraft connection. */
final class NativeLobbyChatScreen extends CompatScreen {
 private final Screen parent;private EditBox input;private Button send;private int left,span,scroll,wait;private long before;private String pending="",sentDraft="";
 NativeLobbyChatScreen(Screen parent){super(L.component(L.s("Чат схватки")));this.parent=parent;}
 private List<NativeNetworkServer.ChatLine> messages(){var l=NativeNetworkClient.lobby;return l==null||l.chat()==null?List.of():l.chat();}
 private void send(){if(!pending.isEmpty()||input.getValue().isBlank())return;sentDraft=input.getValue();pending=sentDraft.strip();before=messages().stream().mapToLong(NativeNetworkServer.ChatLine::id).max().orElse(0);wait=0;NativeNetworkClient.request("lobby_chat",j->j.addProperty("text",pending));send.active=false;}
 @Override protected void init(){String draft=input==null?"":input.getValue();span=Math.min(600,width-24);left=(width-span)/2;
  input=addRenderableWidget(new MenuField(font,left+10,height-78,span-118,20,L.component(L.s("Сообщение"))));input.setMaxLength(200);input.setValue(draft);
  send=addRenderableWidget(MenuButton.styled(L.component(L.s("Отправить")),b->send()).primary().bounds(left+span-100,height-78,90,20).build());
  addRenderableWidget(MenuButton.styled(L.component(L.s("К схватке")),b->onClose()).quiet().bounds(left+10,height-34,span-20,20).build());setInitialFocus(input);
 }
 @Override public void tick(){if(!pending.isEmpty()){if(messages().stream().anyMatch(m->m.id()>before&&m.name().equals(minecraft.player.getName().getString())&&m.text().equals(pending))){if(input.getValue().equals(sentDraft))input.setValue("");pending="";scroll=0;}else if(!NativeNetworkClient.error.isEmpty()||++wait>160)pending="";}send.active=pending.isEmpty()&&!input.getValue().isBlank()&&NativeNetworkClient.lobby!=null;}
 @Override public boolean keyPressed(int key,int scan,int modifiers){if(key==257||key==335){send();return true;}return super.keyPressed(key,scan,modifiers);}
 @Override public boolean mouseScrolled(double x,double y,double h,double v){scroll=Math.max(0,scroll+(int)(v*3));return v!=0;}
 @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta){super.extractBackground(g,mx,my,delta);MenuButton.panel(g,width,height,span);g.centeredText(font,title,width/2,23,UiPaint.ACCENT);
  int top=50,bottom=height-88,step=font.lineHeight+4;var lines=new ArrayList<net.minecraft.util.FormattedCharSequence>();for(var m:messages())lines.addAll(font.split(net.minecraft.network.chat.Component.literal(m.name()+": "+m.text()),span-28));
  int capacity=Math.max(1,(bottom-top)/step);scroll=Math.clamp(scroll,0,Math.max(0,lines.size()-capacity));int first=Math.max(0,lines.size()-capacity-scroll);
  g.enableScissor(left+10,top,left+span-10,Math.max(top+1,bottom));if(lines.isEmpty())g.text(font,L.s("Сообщения видны только участникам комнаты."),left+14,top+4,UiPaint.MUTED,false);else for(int i=first;i<Math.min(lines.size(),first+capacity);i++)g.text(font,lines.get(i),left+14,top+(i-first)*step,UiPaint.TEXT,false);g.disableScissor();
 }
 @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){super.extractRenderState(g,mx,my,delta);String error=NativeNetworkClient.error;if(!error.isBlank())g.text(font,font.plainSubstrByWidth(error,span-20),left+10,height-51,0xffff9b93,false);
 }
 @Override public void onClose(){minecraft.gui.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}

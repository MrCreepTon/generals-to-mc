package local.generalsmc.client;
import local.generalsmc.nativebridge.NativeNetworkServer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import java.util.function.Consumer;
import com.google.gson.JsonObject;

/** One place for a marker's label, note, start settings and removal. */
final class MarkerDetailsScreen extends CompatScreen {
    final Screen parent;NativeNetworkServer.MarkerDetails details;EditBox name,note;String status="";
    MarkerDetailsScreen(Screen parent,NativeNetworkServer.MarkerDetails details){super(L.component(MarkerFinderScreen.kind(details.marker().kind())));this.parent=parent;this.details=details;}
    void request(String kind,Consumer<JsonObject> fill){var p=details.marker();NativeNetworkClient.request(kind,j->{j.addProperty("x",p.x());j.addProperty("y",p.y());j.addProperty("z",p.z());fill.accept(j);});}
    void saved(){status=L.s("Сохранено");}
    void refresh(NativeNetworkServer.MarkerDetails value){details=value;rebuildWidgets();}
    private Button button(String label,int x,int y,int width,Runnable action){return addRenderableWidget(MenuButton.styled(L.component(label),b->action.run()).bounds(x,y,width,20).build());}
    @Override protected void init(){int x=width/2-155;String n=name==null?details.marker().name():name.getValue(),text=note==null?details.marker().note():note.getValue();
        name=addRenderableWidget(new EditBox(font,x,61,310,20,L.component(L.s("Название"))));name.setMaxLength(64);name.setValue(n==null?"":n);name.setEditable(details.editable());
        note=addRenderableWidget(new EditBox(font,x,103,310,20,L.component(L.s("Заметка"))));note.setMaxLength(256);note.setValue(text==null?"":text);note.setEditable(details.editable());
        if(details.start()!=null)button(L.s("Параметры армии"),x,131,310,()->minecraft.setScreen(new NativeStartScreen(this,details.start(),true))).active=details.editable()&&!NativeRtsController.hasSession();
        button(L.s("На карте"),x,height-86,151,()->minecraft.setScreen(StartMapScreen.locate(this,details.marker())));
        button(L.s("Телепортироваться"),x+159,height-86,151,()->request("marker_teleport",j->{})).active=details.editable();
        var delete=button(L.s("Удалить маркер…"),x,height-59,310,()->minecraft.setScreen(new ConfirmScreen(ok->{minecraft.setScreen(this);if(ok)request("marker_delete",j->{});},L.component(L.s("Удалить маркер?")),L.component(name.getValue().isBlank()?title.getString():name.getValue()))));delete.active=details.editable()&&!NativeRtsController.hasSession();
        button(L.s("Сохранить"),x,height-33,200,()->request("marker_save",j->{j.addProperty("name",name.getValue());j.addProperty("note",note.getValue());})).active=details.editable();button(L.s("Назад"),x+208,height-33,102,this::onClose);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float d){MenuButton.panel(g,width,height);super.render(g,mx,my,d);int x=width/2-155;g.drawCenteredString(font,title,width/2,16,0xfff0d68a);var p=details.marker();String text=NativeNetworkClient.error.isEmpty()?status:NativeNetworkClient.error;g.drawCenteredString(font,font.plainSubstrByWidth(text.isEmpty()?p.x()+", "+p.y()+", "+p.z():L.decode(text),304),width/2,33,text.isEmpty()?0xffcccccc:NativeNetworkClient.error.isEmpty()?0xffbbffaa:0xffff8888);if(!text.isEmpty()&&my>=30&&my<46)g.setTooltipForNextFrame(font,font.split(L.component(text),300),mx,my);g.drawString(font,L.s("Название"),x,49,0xffdddddd);g.drawString(font,L.s("Заметка"),x,91,0xffdddddd);}

    @Override public void onClose(){minecraft.setScreen(parent);}@Override public boolean isPauseScreen(){return false;}
}

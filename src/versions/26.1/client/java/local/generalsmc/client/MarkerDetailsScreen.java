package local.generalsmc.client;
import local.generalsmc.nativebridge.NativeNetworkServer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import java.util.function.Consumer;
import com.google.gson.JsonObject;

/** One place for a marker's label, note, start settings and removal. */
final class MarkerDetailsScreen extends CompatScreen {
    final Screen parent;NativeNetworkServer.MarkerDetails details;EditBox name,note;String status="";Button saveButton,backButton;boolean saving,closing,saveFailed;String savedName,savedNote,pendingName,pendingNote;
    MarkerDetailsScreen(Screen parent,NativeNetworkServer.MarkerDetails details){super(L.component(MarkerFinderScreen.kind(details.marker().kind())));this.parent=parent;this.details=details;savedName=clean(details.marker().name());savedNote=clean(details.marker().note());}
    void request(String kind,Consumer<JsonObject> fill){var p=details.marker();NativeNetworkClient.request(kind,j->{j.addProperty("x",p.x());j.addProperty("y",p.y());j.addProperty("z",p.z());fill.accept(j);});}
    private static String clean(String value){return value==null?"":value;}
    boolean dirty(){return name!=null&&(!name.getValue().equals(savedName)||!note.getValue().equals(savedNote));}
    private void changed(){if(!saving)status="";saveAvailability();}
    private void saveAvailability(){if(saveButton!=null)MenuButton.access(saveButton,details.editable()&&dirty()&&!saving,!details.editable()?L.s("Маркер меняет хозяин мира или оператор"):saving?L.s("Сохранение…"):L.s("Нет изменений"));}
    private void save(){
        if(saving||!details.editable()||!dirty())return;
        pendingName=name.getValue();pendingNote=note.getValue();saveFailed=false;if(backButton!=null)backButton.setMessage(L.component(L.s("Назад")));saving=true;status=L.s("Сохранение…");changed();
        request("marker_save",j->{j.addProperty("name",pendingName);j.addProperty("note",pendingNote);});
    }
    void saved(){saving=false;saveFailed=false;savedName=pendingName;savedNote=pendingNote;status=L.s("Сохранено");if(closing){if(dirty())save();else minecraft.setScreen(parent);}else saveAvailability();}
    void deleted(){closing=false;saving=false;if(parent instanceof StartMapScreen)NativeNetworkClient.request("map_points");minecraft.setScreen(parent);}
    @Override public void tick(){if(saving&&!NativeNetworkClient.error.isEmpty()){saving=false;closing=false;saveFailed=true;if(backButton!=null)backButton.setMessage(L.component(L.s("Не сохранять")));changed();}}

    void refresh(NativeNetworkServer.MarkerDetails value){details=value;rebuildWidgets();}
    private Button button(String label,int x,int y,int width,Runnable action){return addRenderableWidget(MenuButton.styled(L.component(label),b->action.run()).bounds(x,y,width,20).build());}
    @Override protected void init(){int x=width/2-155;String n=name==null?details.marker().name():name.getValue(),text=note==null?details.marker().note():note.getValue();
        name=addRenderableWidget(new MenuField(font,x,61,310,20,L.component(L.s("Название"))));name.setMaxLength(64);name.setValue(n==null?"":n);name.setEditable(details.editable());
        note=addRenderableWidget(new MenuField(font,x,103,310,20,L.component(L.s("Заметка"))));note.setMaxLength(256);note.setValue(text==null?"":text);note.setEditable(details.editable());
        if(details.start()!=null)MenuButton.access(button(L.s("Параметры армии"),x,131,310,()->minecraft.setScreen(new NativeStartScreen(this,details.start(),true))),details.editable()&&!NativeRtsController.hasSession(),NativeRtsController.hasSession()?L.s("Сначала завершите текущую RTS"):L.s("Маркер меняет хозяин мира или оператор"));
        button(L.s("На карте"),x,height-86,151,()->minecraft.setScreen(StartMapScreen.locate(this,details.marker())));
        MenuButton.access(button(L.s("Телепортироваться"),x+159,height-86,151,()->request("marker_teleport",j->{})),details.editable(),L.s("Телепортация доступна владельцу мира, оператору или в творческом режиме."));
        var delete=button(L.s("Удалить маркер…"),x,height-59,310,()->minecraft.setScreen(new MenuConfirmScreen(ok->{minecraft.setScreen(this);if(ok)request("marker_delete",j->{});},L.component(L.s("Удалить маркер?")),L.component(name.getValue().isBlank()?title.getString():name.getValue()))));MenuButton.danger(delete);MenuButton.access(delete,details.editable()&&!NativeRtsController.hasSession(),NativeRtsController.hasSession()?L.s("Сначала завершите текущую RTS"):L.s("Маркер меняет хозяин мира или оператор"));
        saveButton=button(L.s("Сохранить"),x,height-33,200,this::save);MenuButton.primary(saveButton);MenuButton.access(saveButton,details.editable()&&dirty()&&!saving,!details.editable()?L.s("Маркер меняет хозяин мира или оператор"):saving?L.s("Сохранение…"):L.s("Нет изменений"));backButton=button(saveFailed?L.s("Не сохранять"):L.s("Назад"),x+208,height-33,102,this::onClose);name.setResponder(v->changed());note.setResponder(v->changed());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float d){MenuButton.panel(g,width,height);super.extractRenderState(g,mx,my,d);int x=width/2-155;g.centeredText(font,title,width/2,16,0xfff0d68a);var p=details.marker();String text=NativeNetworkClient.error.isEmpty()?status:NativeNetworkClient.error;g.centeredText(font,font.plainSubstrByWidth(text.isEmpty()?p.x()+", "+p.y()+", "+p.z():L.decode(text),304),width/2,33,text.isEmpty()?0xffcccccc:NativeNetworkClient.error.isEmpty()?0xffbbffaa:0xffff8888);if(!text.isEmpty()&&my>=30&&my<46)g.setTooltipForNextFrame(font,font.split(L.component(text),300),mx,my);g.text(font,L.s("Название"),x,49,0xffdddddd);g.text(font,L.s("Заметка"),x,91,0xffdddddd);}

    @Override public void onClose(){if(saveFailed){NativeNetworkClient.error="";NativeNetworkClient.message="";minecraft.setScreen(parent);return;}if(details.editable()&&(dirty()||saving)){closing=true;save();}else minecraft.setScreen(parent);}@Override public boolean isPauseScreen(){return false;}
}

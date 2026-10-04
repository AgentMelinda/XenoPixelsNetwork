 package xenoapi.npcs.api.gui;

 import xenoapi.npcs.api.function.EventWrapper;
 import xenoapi.npcs.api.function.gui.GuiComponentAction;

 public interface ISlider extends ICustomGuiComponent {

    float getValue();
    ISlider setValue(float value);

    String getFormat();
    ISlider setFormat(String format);

    float getMin();
    ISlider setMin(float min);

    float getMax();
    ISlider setMax(float max);

    int getDecimals();
    ISlider setDecimals(int i);

     ISlider setOnChange(String id, GuiComponentAction<ISlider> onChange);
     EventWrapper<GuiComponentAction<ISlider>> getOnChangeEvents();
}

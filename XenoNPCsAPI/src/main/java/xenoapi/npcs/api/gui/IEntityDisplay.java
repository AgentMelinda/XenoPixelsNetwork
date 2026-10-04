package xenoapi.npcs.api.gui;

import xenoapi.npcs.api.IWorld;
import xenoapi.npcs.api.entity.IEntity;

public interface IEntityDisplay extends ICustomGuiComponent {

    IEntity getEntity();
    IEntityDisplay setEntity(IEntity entity);

    int getRotation();
    IEntityDisplay setRotation(int rot);

    float getScale();
    IEntityDisplay setScale(float scale);

    boolean getBackground();
    IEntityDisplay setBackground(boolean bo);

}

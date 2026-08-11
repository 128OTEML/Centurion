package centurion.world.blocks.distribution;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.gen.Building;
import mindustry.graphics.Layer;
import mindustry.type.Item;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.distribution.Conveyor;
import mindustry.world.blocks.distribution.ItemBridge;
import mindustry.world.blocks.storage.StorageBlock;

import java.lang.reflect.Field;
import java.util.HashMap;

public class SafetyArm extends Block {

    public TextureRegion baseRegion, stickRegion, supportRegion, armRegion, jointRegion, clawRegion;
    public float linkLength = 16f;
    public int rangeTiles   = 3;

    public float shadowOffset = 2f;

    public static final Color fromColor  = Color.valueOf("4e9af1");
    public static final Color toColor    = Color.valueOf("fed17b");
    public static final Color jointColor = Color.valueOf("2d3142");

    public static SafetyArmBuild selectingArm = null;
    public static int selectingPhase = 0;

    public float moveTime = 60f;

    private static final HashMap<Class<?>, Field[]> BELT_FIELD_CACHE = new HashMap<>();

    private static Field[] resolveBeltFields(Building b) {
        Class<?> cls = b.getClass();
        Field[] cached = BELT_FIELD_CACHE.get(cls);
        if (cached != null) return cached;

        Field fLen = null, fIds = null, fPos = null;
        Class<?> search = cls;

        while (search != null && search != Object.class) {
            if (fLen == null) {
                try { fLen = search.getDeclaredField("len"); fLen.setAccessible(true); } catch (Throwable ignored) {}
            }
            if (fIds == null) {
                try { fIds = search.getDeclaredField("ids"); fIds.setAccessible(true); } catch (Throwable ignored) {}
            }
            if (fPos == null) {
                try { fPos = search.getDeclaredField("pos"); fPos.setAccessible(true); } catch (Throwable ignored) {}
                if (fPos == null) {
                    try { fPos = search.getDeclaredField("position"); fPos.setAccessible(true); } catch (Throwable ignored) {}
                }
            }
            if (fLen != null && fIds != null) break;
            search = search.getSuperclass();
        }

        Field[] result = {fLen, fIds, fPos};
        BELT_FIELD_CACHE.put(cls, result);
        return result;
    }

    public SafetyArm(String name) {
        super(name);
        size         = 2;
        update       = true;
        solid        = true;
        configurable = true;
        saveConfig   = true;

        config(int[].class, (SafetyArmBuild b, int[] v) -> {
            if (v != null && v.length >= 4) {
                b.fromX = v[0]; b.fromY = v[1];
                b.toX   = v[2]; b.toY   = v[3];
                b.fullReset();
            }
        });
    }

    static boolean isSelectable(Building b) {
        if (b == null || b.block == null) return false;
        if (b.block instanceof Conveyor || b.block instanceof ItemBridge) return true;
        if (b.block instanceof StorageBlock) return true;
        if (b instanceof SafetyArmBuild) return true;
        return b.block.itemCapacity > 0 && b.items != null;
    }

    @Override
    public void load() {
        super.load();
        baseRegion    = Core.atlas.find(name + "-base",    region);
        stickRegion   = Core.atlas.find(name + "-stick");
        supportRegion = Core.atlas.find(name + "-support", Core.atlas.find(name + "-supp", region));
        armRegion     = Core.atlas.find(name + "-arm",     region);
        jointRegion   = Core.atlas.find(name + "-joint",   region);
        clawRegion    = Core.atlas.find(name + "-claw",    region);
    }

    public class SafetyArmBuild extends Building {

        public int fromX = -1, fromY = -1;
        public int toX   = -1, toY   = -1;

        public Item heldItem = null;

        public float   armProgress    = 0f;
        public boolean movingToTarget = true;

        public int   armState   = 0;
        public float stateTimer = 0f;

        public int destStallTicks = 0;
        public static final int DEST_STALL_LIMIT = 180;

        public boolean mouseHover = false;
        public long lastTapTime   = 0L;

        public boolean isConfigured() {
            return toX >= 0 && toY >= 0;
        }

        public boolean hasSource() {
            return fromX >= 0 && fromY >= 0;
        }

        /** Detecta automáticamente cualquier computadora en rango por proximidad */
        public computer.ComputerArmBuild getComputer() {
            for (int dx = -rangeTiles; dx <= rangeTiles; dx++) {
                for (int dy = -rangeTiles; dy <= rangeTiles; dy++) {
                    Building b = Vars.world.build(tile.x + dx, tile.y + dy);
                    if (b instanceof computer.ComputerArmBuild comp) {
                        return comp;
                    }
                }
            }
            return null;
        }

        public boolean isItemAllowed(Item item) {
            computer.ComputerArmBuild comp = getComputer();
            if (comp != null) return comp.isItemAllowed(item);
            return true;
        }

        public boolean isReceivingFromArm() {
            for (int dx = -rangeTiles; dx <= rangeTiles; dx++) {
                for (int dy = -rangeTiles; dy <= rangeTiles; dy++) {
                    Building other = Vars.world.build(tile.x + dx, tile.y + dy);
                    if (other instanceof SafetyArmBuild oArm && other != this) {
                        if (oArm.toX == tile.x && oArm.toY == tile.y) return true;
                    }
                }
            }
            return false;
        }

        public void clearLinksInWorld() {
            for (int dx = -rangeTiles; dx <= rangeTiles; dx++) {
                for (int dy = -rangeTiles; dy <= rangeTiles; dy++) {
                    Building other = Vars.world.build(tile.x + dx, tile.y + dy);
                    if (other instanceof SafetyArmBuild oArm && other != this) {
                        if (oArm.toX == tile.x && oArm.toY == tile.y) {
                            oArm.toX = -1; oArm.toY = -1; oArm.fullReset();
                        }
                        if (oArm.fromX == tile.x && oArm.fromY == tile.y) {
                            oArm.fromX = -1; oArm.fromY = -1; oArm.fullReset();
                        }
                    }
                }
            }
        }

        @Override
        public void created() {
            super.created();
            resetArmState();
        }

        @Override
        public void onRemoved() {
            super.onRemoved();
            clearLinksInWorld();
            resetArmState();
        }

        @Override
        public void onDestroyed() {
            super.onDestroyed();
            clearLinksInWorld();
            resetArmState();
        }

        boolean isInRange(int tx, int ty) {
            return Math.abs(tx - tile.x) <= rangeTiles && Math.abs(ty - tile.y) <= rangeTiles;
        }

        float ease(float t) {
            t = Mathf.clamp(t);
            return t * t * (3f - 2f * t);
        }

        private Item extractFromBelt(Building b) {
            Field[] fields = resolveBeltFields(b);
            Field fLen = fields[0], fIds = fields[1], fPos = fields[2];
            if (fLen == null || fIds == null) return null;

            try {
                int len = fLen.getInt(b);
                if (len <= 0) return null;

                Object idsRaw = fIds.get(b);
                if (idsRaw == null) return null;

                float[] pos = null;
                if (fPos != null) {
                    try { pos = (float[]) fPos.get(b); } catch (Throwable ignored) {}
                }

                int targetIdx = -1;

                if (idsRaw instanceof int[] ids) {
                    for (int i = 0; i < len; i++) {
                        Item item = Vars.content.item(ids[i]);
                        if (item != null && isItemAllowed(item)) {
                            targetIdx = i;
                            break;
                        }
                    }
                    if (targetIdx == -1) return null;

                    Item extracted = Vars.content.item(ids[targetIdx]);
                    for (int i = targetIdx; i < len - 1; i++) ids[i] = ids[i + 1];
                    ids[len - 1] = 0;

                    if (pos != null && pos.length >= len) {
                        for (int i = targetIdx; i < len - 1; i++) pos[i] = pos[i + 1];
                        pos[len - 1] = 0f;
                    }
                    fLen.setInt(b, len - 1);
                    if (b.items != null && b.items.get(extracted) > 0) b.items.remove(extracted, 1);
                    return extracted;

                } else if (idsRaw instanceof Item[] ids) {
                    for (int i = 0; i < len; i++) {
                        if (ids[i] != null && isItemAllowed(ids[i])) {
                            targetIdx = i;
                            break;
                        }
                    }
                    if (targetIdx == -1) return null;

                    Item extracted = ids[targetIdx];
                    for (int i = targetIdx; i < len - 1; i++) ids[i] = ids[i + 1];
                    ids[len - 1] = null;

                    if (pos != null && pos.length >= len) {
                        for (int i = targetIdx; i < len - 1; i++) pos[i] = pos[i + 1];
                        pos[len - 1] = 0f;
                    }
                    fLen.setInt(b, len - 1);
                    if (b.items != null && b.items.get(extracted) > 0) b.items.remove(extracted, 1);
                    return extracted;
                }

                return null;
            } catch (Throwable e) {
                return null;
            }
        }

        public Item extractOne(Building b) {
            if (b == null || !b.isValid()) return null;

            if (b instanceof SafetyArmBuild srcArm) {
                if (srcArm.toX >= 0 && (srcArm.toX != tile.x || srcArm.toY != tile.y)) return null;
                if (srcArm.heldItem != null) {
                    if (!isItemAllowed(srcArm.heldItem)) return null;
                    Item taken = srcArm.heldItem;
                    srcArm.heldItem = null;
                    srcArm.armState = 2;
                    srcArm.stateTimer = 0f;
                    return taken;
                }
                return null;
            }

            if (b.block instanceof Conveyor || b.block instanceof ItemBridge) {
                return extractFromBelt(b);
            }

            if (b.items == null || b.items.empty()) return null;

            for (Item it : Vars.content.items()) {
                if (b.items.get(it) > 0 && isItemAllowed(it)) {
                    b.items.remove(it, 1);
                    return it;
                }
            }
            return null;
        }

        boolean destinationHasRoom() {
            Building dest = Vars.world.build(toX, toY);
            if (dest == null || !dest.isValid()) return false;

            if (dest instanceof SafetyArmBuild) {
                return ((SafetyArmBuild) dest).heldItem == null;
            }

            if (dest.block instanceof Conveyor || dest.block instanceof ItemBridge) {
                Field[] f = resolveBeltFields(dest);
                if (f[0] != null) {
                    try { return f[0].getInt(dest) < dest.block.itemCapacity; } catch (Throwable ignored) {}
                }
            }
            return dest.items != null && dest.items.total() < dest.block.itemCapacity;
        }

        private boolean hasRoomFor(Building dest, Item item) {
            if (dest instanceof SafetyArmBuild) return ((SafetyArmBuild) dest).heldItem == null;
            if (dest.block instanceof Conveyor || dest.block instanceof ItemBridge) {
                Field[] f = resolveBeltFields(dest);
                if (f[0] != null) {
                    try { return f[0].getInt(dest) < dest.block.itemCapacity; } catch (Throwable ignored) {}
                }
                return dest.items != null && dest.items.total() < dest.block.itemCapacity;
            }
            if (dest.block.itemCapacity > 0 && dest.items != null) {
                return dest.items.total() < dest.block.itemCapacity;
            }
            return true;
        }

        boolean deliverHeldItem() {
            if (heldItem == null) return true;
            Building dest = Vars.world.build(toX, toY);
            if (dest == null || !dest.isValid()) { heldItem = null; return true; }

            Item item = heldItem;

            if (!hasRoomFor(dest, item)) return false;
            if (armProgress < 0.85f) return false;

            if (dest instanceof SafetyArmBuild otherArm) {
                if (otherArm.heldItem == null && otherArm.isItemAllowed(item)) {
                    otherArm.handleItem(this, item);
                    heldItem = null;
                    return true;
                }
                return false;
            }

            try {
                dest.handleItem(this, item);
            } catch (Throwable e) {
                try { dest.items.add(item, 1); } catch (Throwable ignored) {}
            }
            heldItem = null;
            return true;
        }

        void fullReset() {
            heldItem       = null;
            armState       = 0;
            stateTimer     = 0f;
            armProgress    = 0f;
            movingToTarget = true;
            destStallTicks = 0;
        }

        void resetArmState() {
            fromX = fromY = toX = toY = -1;
            fullReset();
        }

        boolean tryGrab(Building src) {
            if (heldItem != null) return false;
            if (!destinationHasRoom()) return false;
            Item item = extractOne(src);
            if (item == null) return false;
            heldItem    = item;
            armState    = 1;
            stateTimer  = 0f;
            return true;
        }

        @Override
        public void updateTile() {
            float mx = Core.input.mouseWorldX();
            float my = Core.input.mouseWorldY();
            float hs = size * Vars.tilesize / 2f;
            mouseHover = mx >= x - hs && mx <= x + hs && my >= y - hs && my <= y + hs;

            if (selectingArm == this && Core.input.justTouched()) {
                Tile ct = Vars.world.tileWorld(mx, my);
                if (ct != null) {
                    if (Math.abs(ct.x - tile.x) > (size / 2) || Math.abs(ct.y - tile.y) > (size / 2)) {
                        handleClick(ct, ct.build);
                    }
                }
            }

            if (!isConfigured()) { armProgress = 0f; return; }

            Building dstB = Vars.world.build(toX, toY);
            if (dstB == null || !dstB.isValid()) {
                resetArmState();
                Vars.ui.showInfoToast("[scarlet]Destino eliminado: reiniciado", 2f);
                return;
            }

            Building srcB = hasSource() ? Vars.world.build(fromX, fromY) : null;
            if (hasSource() && (srcB == null || !srcB.isValid())) {
                fromX = -1; fromY = -1;
                Vars.ui.showInfoToast("[scarlet]Origen eliminado", 1.8f);
                srcB = null;
            }

            if (heldItem != null && armState == 0) {
                armState   = 1;
                stateTimer = 0f;
            }

            switch (armState) {
                case 0: {
                    armProgress    = 0f;
                    movingToTarget = true;

                    if (srcB != null) tryGrab(srcB);
                    break;
                }
                case 1: {
                    movingToTarget = true;
                    stateTimer    += edelta();
                    armProgress    = ease(stateTimer / moveTime);

                    if (stateTimer >= moveTime) {
                        armProgress = 1f;
                        if (deliverHeldItem()) {
                            destStallTicks = 0;
                            armState       = 2;
                            stateTimer     = 0f;
                        } else {
                            destStallTicks++;
                            if (destStallTicks >= DEST_STALL_LIMIT) {
                                heldItem       = null;
                                destStallTicks = 0;
                                armState       = 2;
                                stateTimer     = 0f;
                            }
                        }
                    }
                    break;
                }
                case 2: {
                    movingToTarget = false;
                    stateTimer    += edelta();
                    armProgress    = 1f - ease(stateTimer / moveTime);

                    if (stateTimer >= moveTime) {
                        armProgress = 0f;
                        stateTimer  = 0f;
                        if (srcB == null || !tryGrab(srcB)) {
                            armState = 0;
                        }
                    }
                    break;
                }
            }
        }

        @Override
        public void tapped() {
            long now = System.currentTimeMillis();

            if (now - lastTapTime < 350L) {
                resetArmState();
                configure(new int[]{-1, -1, -1, -1});
                selectingArm = null;
                selectingPhase = 0;
                lastTapTime = 0L;
                Vars.ui.showInfoToast("[scarlet]Doble clic: Selección borrada", 2f);
                return;
            }

            lastTapTime = now;

            if (selectingArm == this) {
                selectingArm = null;
                selectingPhase = 0;
                Vars.ui.showInfoToast("[scarlet]Selección cancelada", 1.5f);
            } else {
                selectingArm = this;
                if (isReceivingFromArm()) {
                    fromX = -1; fromY = -1;
                    selectingPhase = 2;
                    Vars.ui.showInfoToast("[gold]Recibe de otra garra. [#fed17b]Haz clic en DESTINO", 2.8f);
                } else {
                    selectingPhase = 1;
                    Vars.ui.showInfoToast("[#4e9af1]Clic 1: Selecciona ORIGEN", 2.5f);
                }
            }
        }

        public void handleClick(Tile tile, Building clicked) {
            if (selectingArm != this) return;

            int tx = tile.x, ty = tile.y;

            if (!isInRange(tx, ty)) {
                Vars.ui.showInfoToast("[scarlet]¡Fuera de rango! (máx: área 5x5)", 2f);
                return;
            }

            Building target = clicked != null ? clicked : Vars.world.build(tx, ty);
            if (!isSelectable(target)) {
                Vars.ui.showInfoToast("[scarlet]Selecciona una cinta, contenedor o brazo robótico", 2f);
                return;
            }

            if (selectingPhase == 1) {
                fromX = tx; fromY = ty;
                selectingPhase = 2;
                configure(new int[]{fromX, fromY, toX, toY});
                Vars.ui.showInfoToast("[#4e9af1]¡Origen guardado! [#fed17b]Clic 2: Selecciona DESTINO", 2.8f);
            } else if (selectingPhase == 2) {
                if (tx == fromX && ty == fromY) {
                    Vars.ui.showInfoToast("[scarlet]El Destino no puede ser el mismo que el Origen", 2f);
                    return;
                }
                toX = tx; toY = ty;
                selectingArm = null;
                selectingPhase = 0;
                configure(new int[]{fromX, fromY, toX, toY});
                Vars.ui.showInfoToast("[#fed17b]¡Destino guardado! Configuración completada.", 2.5f);
            }
        }

        @Override
        public void buildConfiguration(Table table) {
            table.defaults().size(160f, 38f).pad(3f);

            table.add(hasSource() ? "[#4e9af1]Origen: [white](" + fromX + ", " + fromY + ")" : "[gray]Origen: Ninguno").padRight(10f);
            table.add(isConfigured() ? "[#fed17b]Destino: [white](" + toX + ", " + toY + ")" : "[gray]Destino: Ninguno");
            table.row();

            table.button("[#4e9af1]Origen", () -> {
                selectingArm   = this;
                selectingPhase = 1;
                Vars.ui.showInfoToast("[#4e9af1]Selecciona ORIGEN en el mapa", 2.5f);
            }).width(160f);

            table.button("[#fed17b]Destino", () -> {
                selectingArm   = this;
                selectingPhase = 2;
                Vars.ui.showInfoToast("[#fed17b]Selecciona DESTINO en el mapa", 2.5f);
            }).width(160f);

            table.row();

            table.button("Borrar Todo", () -> {
                resetArmState();
                configure(new int[]{-1, -1, -1, -1});
                deselect();
                Vars.ui.showInfoToast("[scarlet]Configuración reiniciada", 1.5f);
            }).colspan(2).width(326f);
        }

        @Override
        public boolean acceptItem(Building source, Item item) {
            if (heldItem != null) return false;
            return isItemAllowed(item);
        }

        @Override
        public void handleItem(Building source, Item item) {
            if (heldItem != null || !isItemAllowed(item)) return;
            heldItem = item;
            if (armState == 0) {
                armState   = 1;
                stateTimer = 0f;
            }
        }

        @Override
        public void draw() {
            Draw.rect(baseRegion != null && baseRegion.found() ? baseRegion : region, x, y);

            Draw.z(Layer.block + 0.05f);

            boolean showMarkers = (mouseHover || selectingArm == this);
            if (showMarkers) {
                if (fromX >= 0) drawMarker(fromX, fromY, fromColor);
                if (toX >= 0)   drawMarker(toX, toY, toColor);
            }
            Draw.color();

            if (selectingArm == this) {
                Color hl = (selectingPhase == 1) ? fromColor : toColor;
                Draw.z(Layer.block + 0.06f);
                Draw.color(hl, Mathf.absin(6f, 0.5f) + 0.3f);
                Lines.stroke(1.5f);
                Lines.rect(x - size * Vars.tilesize / 2f, y - size * Vars.tilesize / 2f,
                        size * Vars.tilesize, size * Vars.tilesize);
                Draw.color();
            }

            float targetX, targetY;
            float groundGx = x, groundGy = y;
            float heightArc = 0f;

            if (isConfigured()) {
                Building sb = hasSource() ? Vars.world.build(fromX, fromY) : null;
                Building db = Vars.world.build(toX, toY);
                float sx = sb != null ? sb.x : x;
                float sy = sb != null ? sb.y : y;
                float ex = db != null ? db.x : x;
                float ey = db != null ? db.y : y;

                float easedProgress = easeInOutCubic(armProgress);

                groundGx = Mathf.lerp(sx, ex, easedProgress);
                groundGy = Mathf.lerp(sy, ey, easedProgress);

                targetX = groundGx;
                targetY = groundGy;

                float dist = Mathf.dst(sx, sy, ex, ey);

                float extensionRatio = Mathf.clamp(dist / (linkLength * 2f), 0.3f, 1.2f);
                float extensionFlatten = 1f - Mathf.clamp((extensionRatio - 0.4f) * 0.6f, 0f, 0.55f);

                float arcH = Math.min(dist * 0.28f, 22f) * extensionFlatten;

                heightArc = Mathf.sin(easedProgress * Mathf.pi) * extensionFlatten;
                targetY += heightArc * arcH;
            } else {
                targetX = x; targetY = y + 8f;
                groundGx = targetX; groundGy = targetY;
            }

            if (heightArc > 0.05f) {
                Draw.z(Layer.block + 0.01f);
                Draw.color(0f, 0f, 0f, 0.28f * heightArc);
                Fill.circle(groundGx, groundGy - 2f, 3.5f + heightArc * 1.5f);
                Draw.color();
            }

            float suppW = (supportRegion != null && supportRegion.found()) ? supportRegion.width * Draw.scl : linkLength;
            float suppH = (supportRegion != null && supportRegion.found()) ? supportRegion.height * Draw.scl : 6f;

            float armW = (armRegion != null && armRegion.found()) ? armRegion.width * Draw.scl : linkLength;
            float armH = (armRegion != null && armRegion.found()) ? armRegion.height * Draw.scl : 5f;

            float stickW = (stickRegion != null && stickRegion.found()) ? stickRegion.width * Draw.scl : 8f;
            float stickH = (stickRegion != null && stickRegion.found()) ? stickRegion.height * Draw.scl : 8f;

            float foreshorten = 1f - heightArc * 0.12f;

            float kdx = targetX - x;
            float kdy = targetY - y;
            float targetDist = Mathf.len(kdx, kdy);

            float nativeTotalReach = suppW + armW;
            float minReach = nativeTotalReach * 0.45f;
            float dist = Math.max(targetDist, minReach);

            float stretchRatio = Mathf.clamp(dist / nativeTotalReach, 0.65f, 1.6f);

            float drawSupportLen = suppW * stretchRatio * foreshorten;
            float drawArmLen     = armW * stretchRatio * foreshorten;

            float aClaw = Mathf.angle(kdx, kdy);

            int armSeed  = (tile.x * 73856093) ^ (tile.y * 19349663);
            float armVar = ((armSeed & 0xF) - 7) * 0.8f;

            float elbowSide = -1f + 2f * smoothBlend(armProgress);
            float shoulder  = aClaw + elbowSide * (22f + armVar) * 0.85f;

            float ex2 = x + Mathf.cosDeg(shoulder) * drawSupportLen;
            float ey2 = y + Mathf.sinDeg(shoulder) * drawSupportLen + (heightArc * 5f);

            float fore = Mathf.angle(targetX - ex2, targetY - ey2);

            float gx = ex2 + Mathf.cosDeg(fore) * drawArmLen;
            float gy = ey2 + Mathf.sinDeg(fore) * drawArmLen;

            float armZ = Layer.turret + ((armSeed & 0xFF) / 255f) * 0.5f;

            if (shadowOffset > 0f) {
                Draw.z(Layer.block + 0.02f);
                Draw.color(Color.black, 0.35f);

                float sOffX = -shadowOffset;
                float sOffYFixed = -shadowOffset;
                float sOffYDynamic = -shadowOffset - (heightArc * 3.5f);

                if (stickRegion != null && stickRegion.found()) {
                    Draw.rect(stickRegion, x + sOffX, y + sOffYFixed, stickW, stickH, shoulder);
                }
                if (supportRegion != null && supportRegion.found()) {
                    Draw.rect(supportRegion, (x + ex2) / 2f + sOffX, (y + ey2) / 2f + sOffYDynamic, drawSupportLen, suppH, shoulder);
                }
                if (armRegion != null && armRegion.found()) {
                    Draw.rect(armRegion, (ex2 + gx) / 2f + sOffX, (ey2 + gy) / 2f + sOffYDynamic, drawArmLen, armH, fore);
                }
                if (jointRegion != null && jointRegion.found()) {
                    Draw.rect(jointRegion, ex2 + sOffX, ey2 + sOffYDynamic, jointRegion.width * Draw.scl, jointRegion.height * Draw.scl, fore);
                }
                if (clawRegion != null && clawRegion.found()) {
                    Draw.rect(clawRegion, gx + sOffX, gy + sOffYDynamic, clawRegion.width * Draw.scl, clawRegion.height * Draw.scl, fore);
                }
                if (heldItem != null) {
                    TextureRegion icon = (heldItem.fullIcon != null && heldItem.fullIcon.found()) ? heldItem.fullIcon : heldItem.uiIcon;
                    if (icon != null && icon.found()) {
                        Draw.rect(icon, gx + sOffX, gy + sOffYDynamic, 6f, 6f);
                    }
                }
                Draw.color();
            }

            Draw.z(armZ - 0.02f);
            if (stickRegion != null && stickRegion.found()) {
                Draw.color(Color.white);
                Draw.rect(stickRegion, x, y, stickW, stickH, shoulder);
            } else {
                Draw.color(jointColor); Fill.circle(x, y, 5f);
                Draw.color(Color.valueOf("4a4f63")); Fill.circle(x, y, 3f);
            }

            Draw.z(armZ);
            if (supportRegion != null && supportRegion.found()) {
                Draw.color(Color.white);
                Draw.rect(supportRegion, (x + ex2) / 2f, (y + ey2) / 2f, drawSupportLen, suppH, shoulder);
            } else {
                Draw.color(jointColor); Lines.stroke(6f); Lines.line(x, y, ex2, ey2);
                Draw.color(fromColor);  Lines.stroke(3.5f); Lines.line(x, y, ex2, ey2);
            }

            if (heldItem != null) {
                TextureRegion icon = (heldItem.fullIcon != null && heldItem.fullIcon.found()) ? heldItem.fullIcon : heldItem.uiIcon;
                if (icon != null && icon.found()) {
                    Draw.z(armZ + 0.005f);
                    Draw.color(Color.white);
                    Draw.rect(icon, gx, gy, 6f, 6f);
                    Draw.color();
                }
            }

            Draw.z(armZ + 0.01f);
            if (armRegion != null && armRegion.found()) {
                Draw.color(Color.white);
                Draw.rect(armRegion, (ex2 + gx) / 2f, (ey2 + gy) / 2f, drawArmLen, armH, fore);
            } else {
                Draw.color(jointColor); Lines.stroke(5f); Lines.line(ex2, ey2, gx, gy);
                Draw.color(toColor);    Lines.stroke(2.8f); Lines.line(ex2, ey2, gx, gy);
            }

            Draw.z(armZ + 0.03f);
            if (jointRegion != null && jointRegion.found()) {
                Draw.color(Color.white);
                Draw.rect(jointRegion, ex2, ey2, jointRegion.width * Draw.scl, jointRegion.height * Draw.scl, fore);
            } else {
                Draw.color(jointColor); Fill.circle(ex2, ey2, 4f);
                Draw.color(toColor);    Fill.circle(ex2, ey2, 2f);
            }

            Draw.z(armZ + 0.04f);
            if (clawRegion != null && clawRegion.found()) {
                Draw.color(Color.white);
                Draw.rect(clawRegion, gx, gy, clawRegion.width * Draw.scl, clawRegion.height * Draw.scl, fore);
            } else {
                Draw.color(jointColor);
                Fill.circle(gx, gy, 3.5f);
                Draw.color(toColor);
                float pX1 = gx + Mathf.cosDeg(fore + 35f) * 5f;
                float pY1 = gy + Mathf.sinDeg(fore + 35f) * 5f;
                float pX2 = gx + Mathf.cosDeg(fore - 35f) * 5f;
                float pY2 = gy + Mathf.sinDeg(fore - 35f) * 5f;
                Lines.stroke(2f);
                Lines.line(gx, gy, pX1, pY1);
                Lines.line(gx, gy, pX2, pY2);
            }

            Draw.color();
        }

        float easeInOutCubic(float t) {
            t = Mathf.clamp(t);
            return t < 0.5f ? 4f * t * t * t : 1f - Mathf.pow(-2f * t + 2f, 3f) / 2f;
        }

        float smoothBlend(float t) {
            t = Mathf.clamp(t);
            return t * t * t * (t * (t * 6f - 15f) + 10f);
        }

        private void drawMarker(int tx, int ty, Color color) {
            Building b = Vars.world.build(tx, ty);
            if (b == null) return;
            float sz = b.block.size * Vars.tilesize;
            float hs = sz / 2f;

            Draw.color(color, 0.2f);
            Fill.rect(b.x, b.y, sz, sz);
            Draw.color(color, 0.85f);
            Lines.stroke(1.2f);
            Lines.rect(b.x - hs, b.y - hs, sz, sz);

            Draw.color(color, Mathf.absin(5f, 0.4f) + 0.6f);
            Fill.square(b.x, b.y, 2f);
        }

        @Override
        public int[] config() {
            return new int[]{fromX, fromY, toX, toY};
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(fromX); write.i(fromY);
            write.i(toX);   write.i(toY);
            write.i(armState);
            write.f(stateTimer);
            write.f(armProgress);
            write.bool(movingToTarget);
            write.s(heldItem != null ? heldItem.id : -1);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            fromX = read.i(); fromY = read.i();
            toX   = read.i(); toY   = read.i();
            armState       = read.i();
            stateTimer     = read.f();
            armProgress    = read.f();
            movingToTarget = read.bool();
            int id = read.s();
            heldItem = id >= 0 ? Vars.content.item(id) : null;
        }
    }
}
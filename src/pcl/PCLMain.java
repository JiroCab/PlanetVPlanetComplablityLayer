package pcl;

import arc.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import arc.util.Timer;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.*;
import mindustry.ctype.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.mod.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.environment.*;

import java.util.*;

import static mindustry.content.Blocks.*;

public class PCLMain extends Plugin{
    public Seq<Block>[]
        index,
        //Ores are based on the metal floor they're on
        //any, metal floors 1-5, dmg m flr, dark pnnel 1-6, any wall, dark metal, dacite, dirt, snow, salt, regolith, stone, ferric stone
        oreIndex,
        //liquidIndex water, oil, slag, cryo
        liquidIndex
    ;
    public Seq<UnitType>[] unitIndex;

    public Seq<Block> blockBlacklist = new Seq();
    public Rules[] rules;
    public Seq<Block> oreDexFloor;



    //called when game initializes
    @Override
    public void init(){
        Events.on(WorldLoadEvent.class, e -> updateRuleSets());

        Events.on(ServerLoadEvent.class, event -> {
            buildPlanetConetent();
        });

        Events.run(EventType.Trigger.update, () -> {
            for(Unit unit : Groups.unit){
                if(unit.spawnedByCore) continue;
                if(unit.isFlying() && !unit.moving()) continue;

                int pla = -1;
                for(int i = 0; i < Vars.content.planets().size; i++){
                    if(unitIndex[i].contains(unit.type)){
                        pla = i;
                        break;
                    }
                }

                Tile t = unit.tileOn();
                if(t.floor() == null) continue;
                float x = t.x, y = t.y, size = (unit.hitSize /16f);

                int x2 = Math.round(x + size),
                y2 = Math.round(y + size),
                x1 = Math.round(x - size),
                y1= Math.round(y - size);

                for(int xf = x1; xf < x2; xf++){
                    for(int yf = y1; yf < y2; yf++){
                        Vars.world.tile(xf, yf).setFloorNet(getFloor(xf, yf, pla), getOre(xf, yf, pla));
                    }
                }

            }
        });


        Events.on(BlockBuildEndEvent.class, event -> {
            if(event.tile == null) return;
            if(event.tile.build == null) return;
            if(!event.tile.isCenter()) return;

            int id = getPlanet(event.tile.build.block);
            if(id < 0){
                Log.debug("PCL no id:" + event.tile.build.block);
                return;
            }

            float size = event.tile.build.block.size;
            float x = event.tile.centerX(), y = event.tile.centerY();
            if(size % 2 == 0) {
                x += 1;
                y += 1;
            }

            size = (size+2)/  2f;


            int x2 = Math.round(x + size),
                    y2 = Math.round(y + size),
                    x1 = Math.round(x - size),
                    y1= Math.round(y - size);


            for(int xf = x1; xf < x2; xf++){
                for(int yf = y1; yf < y2; yf++){
                    Vars.world.tile(xf, yf).setFloorNet(getFloor(xf, yf, id), getOre(xf, yf, id));
                }
            }
        });

    }

    public Floor getFloor(int x, int y, int id){
        Tile t = Vars.world.tile(x, y);
        if(blockBlacklist.contains(t.floor() )) return t.floor();
        if(t.floor().isLiquid){
            return t.floor();
        };
        if(id == -1 || index[id].size <= 0) return t.floor();
        //todo liquid manip

        if(index[id].first() instanceof  Floor) return (Floor)index[id].first();
        return t.floor();
    }

    public Floor getOre(int x, int y, int id){
        Tile t = Vars.world.tile(x, y);
        if(t.overlay() == air) return t.asFloor();
        if(id == -1 || oreIndex[id].size <= 0) return t.overlay();


        if(t.block() != air && t.build == null){
            if(oreDexFloor.contains(t.block())){
                Block o = oreIndex[id].get(oreDexFloor.indexOf(t.block()));
                if(o == air || o == space || o == empty) return t.overlay();
                else return o.asFloor();
            }
            else return oreIndex[id].get(13).asFloor();
        }else {
            if(oreDexFloor.contains(t.floor())){
                Block o =oreIndex[id].get(oreDexFloor.indexOf(t.floor()));
                if(o == air || o == space || o == empty) return t.overlay();
                else return o.asFloor();
            }
            else return oreIndex[id].get(0).asFloor();
        }
    }

    public int getPlanet(Block block){
        for(int i = 0; i < Vars.content.planets().size; i++){
            if(index[i] == null) continue;

            if(index[i].contains(block)) return i;
        }
        return -1;
    }

    public void buildPlanetConetent (){
        index = new Seq[Vars.content.planets().size +1];
        unitIndex = new Seq[Vars.content.planets().size +1];
        for(int i = 0; i < Vars.content.planets().size; i++){
            Planet p = Vars.content.planets().get(i);
            index[i] = new Seq();
            unitIndex[i] = new Seq();

            if(p.techTree == null) continue;

            getPlanetFirst(p, i);

            int finalI = i;
            p.techTree.each(n -> {
                if(n.content.getContentType() == ContentType.block) index[finalI].add((Block)n.content);
                if(n.content.getContentType() == ContentType.unit) unitIndex[finalI].add((UnitType)n.content);

            });
        }

        readOres();
        readBlockBlacklist();
        logPlanetInfos();


    }

    public void getPlanetFirst (Planet p, int ind){
        //read here
        String in = Core.settings.getString("pcl-spread-" + p.name, "");
        if(p.isModded() && in.isEmpty()) in = Core.settings.getString("pcl-spread-" + p.minfo.mod.name, "");
        if(!in.isEmpty()){
            Block bl = Vars.content.getByName(ContentType.block, in);
            if(bl != null) {
                index[ind].add(bl);
                return;
            }
        }


        if(p == Planets.serpulo) index[ind].add(darksand);
        else  if (p == Planets.erekir) index[ind].add(yellowStone);
    }

    public void logPlanetInfos(){


        StringBuilder blk  = new StringBuilder(), unt = new StringBuilder(), pln = new StringBuilder();
        for(int i = 0; i < Vars.content.planets().size; i++){
            Planet p = Vars.content.planets().get(i);
            if(index[i] != null && index[i].size > 0){
                blk.append(p.name).append(" - ").append(index[i].toString()).append("\n");
            }

            if(unitIndex[i] != null && unitIndex[i].size != 0){
                unt.append(p.name).append(" - ").append(unitIndex[i].toString()).append("\n");
            }
            if(oreIndex[i] != null && oreIndex[i].size != 0){
                pln.append(p.name).append(" - ").append(oreIndex[i]).append("\n");
            }

        }

        Log.info("=== PCL - Planet Blocks === \n" + blk);
        Log.info("=== PCL - Planet Units === \n" + unt);
        Log.info("=== PCL - Planet Ore === \n" + pln);
        Log.info("=== PCL - Floor Blacklist === \n" + blockBlacklist);
        Log.info("===  === ===");

    }

    public void readOres(){
        oreIndex = new Seq[Vars.content.planets().size +1];

        for(int ind = 0; ind < Vars.content.planets().size; ind++){
            Planet p = Vars.content.planets().get(ind);
            Seq ores = new Seq();

            //Hardcoded bc lazy
            if(p == Planets.serpulo){
                ores.addAll(
                oreCopper, //any
                oreCopper, // metal flr
                oreCopper,
                oreLead,
                oreLead,
                oreLead,
                oreCoal, //dmg flr
                oreCoal, // dark pnl
                oreCoal,
                oreTitanium,
                oreTitanium,
                oreThorium,
                oreThorium,
                air, // any wall
                air, // darkmetal
                air, // dacite
                air, // dirt
                air, // snow
                air, // salt
                air, // regolith
                air, // stone
                air // ferric stne

                );
            }else if(p == Planets.erekir){
                ores.addAll(
                oreTungsten, //any
                oreTungsten, // metal flr
                oreCrystalThorium,
                oreCrystalThorium,
                oreBeryllium,
                oreBeryllium,
                oreCrystalThorium, // dark pnl
                oreCrystalThorium,
                oreTungsten,
                oreTungsten,
                oreCrystalThorium,
                oreCrystalThorium,
                wallOreBeryllium, // any wall
                wallOreBeryllium, // darkmetal
                wallOreBeryllium, // dacite
                wallOreThorium, // dirt
                wallOreThorium, // snow
                wallOreThorium, // salt
                wallOreTungsten, // regolith
                wallOreTungsten, // stone
                wallOreTungsten // ferric stne
                );
            }
            oreIndex[ind] = ores;
        }

        Vars.mods.eachEnabled(m -> {
            //for all planets of a mod
            String in = Core.settings.getString("pcl-ores-" + m.name, "");
            if(!in.isEmpty()){
                String[] pair = in.split(",");

                for(String s : pair){
                    Block b = s.isEmpty() ? air : Vars.content.getByName(ContentType.block, s);
                    for(int i = 0; i < Vars.content.planets().size; i++){
                        Planet planet = Vars.content.planets().get(i);
                        if(planet.isVanilla()) continue;
                        if(planet.minfo.mod != m) continue;

                        oreIndex[i].add(b);
                    }
                }
            }

            //for a single planet
            Seq<Planet> p = Vars.content.planets().copy();
            p.retainAll(pl -> pl.minfo.mod == m);

            for(Planet planet : p){
                String pl = Core.settings.getString("pcl-ores-" + planet.name,"");
                if(pl.isEmpty()) continue;
                String[] pOre = in.split(",");
                for(String s : pOre){
                    Block b = Vars.content.getByName(ContentType.block, s);
                    oreIndex[Vars.content.planets().indexOf(planet)].addUnique(b);
                }
            }

        });




    }

    public void  readBlockBlacklist(){
        oreDexFloor = Seq.with(air, metalFloor, metalFloor2, metalFloor3, metalFloor4, metalFloor5, metalFloorDamaged, darkPanel1, darkPanel2, darkPanel3, darkPanel4, darkPanel5, darkPanel6, empty, darkMetal, daciteWall, dirtWall, snowWall, saltWall, regolithWall, stoneWall, ferricStoneWall);
        blockBlacklist.add(oreDexFloor);
        blockBlacklist.addAll(space, coreZone, rhyoliteVent, carbonVent, arkyicVent, yellowStoneVent, redStoneVent, crystallineVent, stoneVent, basaltVent);

        Vars.mods.eachEnabled(m -> {
            String in = Core.settings.getString("pcl-blacklist-" + m.name, "");
            if(!in.isEmpty()){
                String[] pair = in.split(",");

                for(String s : pair){
                    Block b = Vars.content.getByName(ContentType.block, s);
                    if(b != null) blockBlacklist.add(b);
                }
            }
        });
    }

    public void  updateRuleSets(){
        Timer.schedule(() ->{
            rules = new Rules[Vars.content.planets().size + 1];

            for(int i = 0; i < Vars.content.planets().size; i++){
                rules[i] = Vars.state.rules.copy();

                if(i > 0){
                    rules[i].blockWhitelist  = true;
                    rules[i].hideBannedBlocks = true;
                    rules[i].bannedBlocks.addAll(index[i]);
                }
                rules[i].planet = Vars.content.planets().get(i);
            }
        }, 0.1f * Time.toSeconds, 0, 1);

    }

    //register commands that run on the server
    @Override
    public void registerServerCommands(CommandHandler handler){

    }

    //register commands that player can invoke in-game
    @Override
    public void registerClientCommands(CommandHandler handler){
        handler.<Player>register("planet", "<planet/-l...>", "Set your blocks to a certain planet! -l instead for list ", (args, player) -> {
            if(args[0].isEmpty()){
                player.sendMessage(args[0] + " is not a planet!");
                return;
            }

            if(args[0].equals("-l")){
                StringBuilder owo = new StringBuilder();
                owo.append(" Planets available:\n");
                for(Planet planet : Vars.content.planets()){
                    if(!planet.accessible) continue;
                    String n = planet.name;
                    if(planet.isModded()){
                        owo.append("  - ").append(n.replaceFirst(planet.minfo.mod.name + "-", "")).append(" [lightgray]/ ").append(n).append("[]");

                    }else owo.append("  - ").append(n);


                    owo.append("\n");
                }
                player.sendMessage(owo.toString());
                return;
            }
            int out = -1;

            for(Planet planet : Vars.content.planets()){
                if(planet.name.equals(args[0])){
                    out = planet.id;
                    break;
                }else {
                    if(!planet.isModded()) continue;
                    String n = planet.name;
                    if(args[0].equals(n.replaceFirst(planet.minfo.mod.name + "-",""))){
                        out = planet.id;
                        break;
                    }
                }
            }

            if(out == -1) {
                player.sendMessage(args[0] + " is not a planet!");
                return;
            }

            Rules r = rules[out];
            r.env = Vars.state.rules.env;
            Call.setRules(player.con, r);
        });
    }
}











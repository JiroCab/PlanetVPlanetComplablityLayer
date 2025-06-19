# PCL
Planet v planet /mix tech with a twist! 

# for mod devs:
add a `Core.settings.put(<see bellow>, <Data>)` for the following to add support
- `pcl-blacklist-<modname>`: blacklist for floors to not get replaced
- `pcl-spread-<modname/single planet name>`: a single floor name that will be cosidered the floor for a mod's planet/ singular planet
- `pcl-ores-<modname/single planet name>` : what ores will change into depending on floor/block in this order:
  - (any floor), metal floor1, metal floor2, ... metal floor6, damaged metal floor, dark panel 1, ... dark panel6, (any wall), dark metal, dirt wall, snow wall, salt wall, regolith wall, stone wall, ferric stone wall
  - leave blank or just `","` and those cases will leave the ore as is

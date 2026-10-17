# Empty world: only an executing creative/spectator test player may enter.
execute if entity @s[type=minecraft:player,gamemode=creative] in xenospace_spike:orbit run tp @s 0 128 0
execute if entity @s[type=minecraft:player,gamemode=spectator] in xenospace_spike:orbit run tp @s 0 128 0
execute if entity @s[type=minecraft:player] unless entity @s[gamemode=creative] unless entity @s[gamemode=spectator] run tellraw @s {"text":"Orbit test entry requires creative or spectator mode in a disposable world.","color":"red"}

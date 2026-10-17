# Read-only context queries. No terrain lookup, force-load mutation or automatic scheduling.
execute in xenospace_spike:surface run time query gametime
execute in xenospace_spike:surface run forceload query
execute in xenospace_spike:orbit run time query gametime
execute in xenospace_spike:orbit run forceload query

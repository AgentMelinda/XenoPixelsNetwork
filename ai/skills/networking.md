# Skill: Networking

- Never insert addon packets into `ModNetwork`; its sequential ids and exact protocol 63 are a
  compatibility contract.
- Register addon packets through `AddonNetwork` from the addon mod constructor using unique
  namespaced ids.
- Keep packet direction explicit, bound lengths/counts during decode, and validate sender/state on
  the receiving side.
- Queue game-state work through the provided context.
- Any change to packet descriptors changes the addon protocol fingerprint; test matching and
  mismatching client/server registries.
- The underlying DMZ SimpleChannel shim is a dependency risk and must be re-inspected after jar
  replacement.

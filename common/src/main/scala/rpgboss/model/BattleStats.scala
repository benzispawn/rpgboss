package rpgboss.model

case class Curve(var base: Double, var perLevel: Double) {
  def apply(x: Int): Int = {
    (perLevel * (x - 1) + base).round.toInt
  }
}

case class StatProgressions(
  var exp: Curve = Curve(300, 100), // Exp required to level up. Not cumulative.
  var mhp: Curve = Curve(50, 10),
  var mmp: Curve = Curve(20, 4),
  var atk: Curve = Curve(10, 2),
  var spd: Curve = Curve(10, 2),
  var mag: Curve = Curve(10, 2),
  var arm: Curve = Curve(10, 1),
  var mre: Curve = Curve(10, 1))

/** Holds the intrinsic stats of the entity without any equipment or temporary
 *  status effects.
 */
case class BaseStats(
  mhp: Int,
  mmp: Int,
  atk: Int,
  spd: Int,
  mag: Int,
  arm: Int,
  mre: Int,
  effects: Seq[Effect])

case class StatusEffect(
  var name: String = "",
  var effects: Seq[Effect] = Seq(),
  var releaseOnBattleEnd: Boolean = false,
  var releaseTime: Int = 0,
  var releaseChance: Int = 0,
  var releaseDmgChance: Int = 0,
  var maxStacks: Int = 1) extends HasName

case class BattleStats(
  mhp: Int,
  mmp: Int,
  atk: Int,
  spd: Int,
  mag: Int,
  arm: Int,
  mre: Int,
  statusEffects: Seq[StatusEffect])

object BattleStats {
  def apply(pData: ProjectData, baseStats: BaseStats,
            equippedIds: Seq[Int] = Seq(),
            tempStatusEffectIds: Seq[Int] = Seq()): BattleStats = {
    require(equippedIds.forall(i => i >= 0 && i < pData.enums.items.length))
    require(tempStatusEffectIds.forall(
      i => i >= 0 && i < pData.enums.statusEffects.length))

    val equipment = equippedIds.map(pData.enums.items)
    val equipmentEffects = equipment.flatMap(_.effects)

    // Make sure we don't apply more than max-stacks of each status effect
    val stackedStatusEffects: Seq[StatusEffect] = {
      val statusEffectStackMap = collection.mutable.Map[Int, Int]()
      val statusEffectBuffer = collection.mutable.ArrayBuffer[StatusEffect]()

      val equipmentStatusEffectIds =
        equipmentEffects
          .filter(_.keyId == EffectKey.AddStatusEffect.id)
          .map(_.v1)
          .filter(_ >= 0)
          .filter(_ < pData.enums.statusEffects.length)

      for (statusEffectId <- equipmentStatusEffectIds ++ tempStatusEffectIds) {
        val statusEffect = pData.enums.statusEffects(statusEffectId)
        val currentCount =
          statusEffectStackMap.getOrElseUpdate(statusEffectId, 0)

        if (currentCount < statusEffect.maxStacks) {
          statusEffectStackMap.update(statusEffectId, currentCount + 1)
          statusEffectBuffer.append(statusEffect)
        }
      }

      statusEffectBuffer.toList
    }

    val allEffects = baseStats.effects ++ equipmentEffects ++
                     stackedStatusEffects.flatMap(_.effects)

    def addEffects(key: EffectKey.Value): Int =
      allEffects.filter(_.keyId == key.id).map(_.v1).sum

    apply(
      mhp = baseStats.mhp + addEffects(EffectKey.MhpAdd),
      mmp = baseStats.mmp + addEffects(EffectKey.MmpAdd),
      atk = baseStats.atk + addEffects(EffectKey.AtkAdd),
      spd = baseStats.spd + addEffects(EffectKey.SpdAdd),
      mag = baseStats.mag + addEffects(EffectKey.MagAdd),
      arm = baseStats.arm + addEffects(EffectKey.ArmAdd),
      mre = baseStats.mre + addEffects(EffectKey.MreAdd),
      statusEffects = stackedStatusEffects
    )
  }
}


package com.ascendsystem.app.core.rules

import com.ascendsystem.app.core.domain.*
import org.junit.Assert.*
import org.junit.Test

class EnginesTest {
    private val quest = Quest(
        "q1", "Corrective focus", "Timer", QuestType.TRIGGERED,
        VerificationType.TIMER, 5, 20
    )

    @Test fun `state machine accepts valid transition and rejects invalid one`() {
        val machine = QuestStateMachine()
        assertEquals(SystemState.WARNING, machine.transition(SystemState.IDLE, SystemState.WARNING))
        assertFalse(machine.canTransition(SystemState.IDLE, SystemState.QUEST_COMPLETED))
    }

    @Test fun `rule respects usage threshold cooldown and cap`() {
        val engine = RuleEngine()
        assertFalse(engine.evaluateTriggeredQuest(quest, 10, 25, 0, 3, true, UserContext()).allowed)
        assertFalse(engine.evaluateTriggeredQuest(quest, 30, 25, 0, 3, false, UserContext()).allowed)
        assertFalse(engine.evaluateTriggeredQuest(quest, 30, 25, 3, 3, true, UserContext()).allowed)
        assertTrue(engine.evaluateTriggeredQuest(quest, 30, 25, 0, 3, true, UserContext()).allowed)
    }

    @Test fun `physical quest becomes recovery while driving`() {
        val physical = quest.copy(verificationType = VerificationType.MANUAL_DEMO)
        val decision = SafetyEngine().validate(physical, UserContext(isDriving = true))
        assertFalse(decision.allowed)
        assertEquals(SystemState.RECOVERY_MODE, decision.nextState)
    }

    @Test fun `overnight schedule crosses midnight`() {
        val engine = ScheduleEngine()
        assertTrue(engine.isWithinWindow(23 * 60, 22 * 60 + 30, 5 * 60))
        assertTrue(engine.isWithinWindow(4 * 60, 22 * 60 + 30, 5 * 60))
        assertFalse(engine.isWithinWindow(12 * 60, 22 * 60 + 30, 5 * 60))
    }

    @Test fun `progression is deterministic`() {
        assertEquals(1, Progression.levelForXp(0))
        assertEquals(2, Progression.levelForXp(100))
        assertEquals(3, Progression.levelForXp(400))
        assertEquals(900, Progression.xpForLevel(4))
    }
}

package com.yadea.smartmoto.maps

import org.junit.Assert.assertEquals
import org.junit.Test

class EntryRouterTest {
    @Test fun firstLaunchNeedsGuide() = assertEquals(EntryRouter.Destination.GUIDE, EntryRouter.destination(false, false, false, false, false))
    @Test fun completedGuideNeedsMapChoice() = assertEquals(EntryRouter.Destination.MAP_SELECTION, EntryRouter.destination(true, true, true, false, false))
    @Test fun configuredLaunchEntersMainDirectly() = assertEquals(EntryRouter.Destination.MAIN, EntryRouter.destination(true, true, true, true, false))
    @Test fun explicitSwitchAlwaysShowsMaps() = assertEquals(EntryRouter.Destination.MAP_SELECTION, EntryRouter.destination(true, true, true, true, true))
    @Test fun revokedConsentNeverEntersMaps() = assertEquals(EntryRouter.Destination.GUIDE, EntryRouter.destination(false, true, true, true, false))
    @Test fun revokedLocationNeedsGuide() = assertEquals(EntryRouter.Destination.GUIDE, EntryRouter.destination(true, false, true, true, false))
    @Test fun revokedMockPermissionNeedsGuide() = assertEquals(EntryRouter.Destination.GUIDE, EntryRouter.destination(true, true, false, true, false))
}

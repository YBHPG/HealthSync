import androidx.health.connect.client.records.*
import androidx.health.connect.client.permission.HealthPermission

fun main() {
    val reqs = setOf(
        HealthPermission.getWritePermission(MenstruationFlowRecord::class),
        HealthPermission.getWritePermission(IntermenstrualBleedingRecord::class),
        HealthPermission.getWritePermission(CervicalMucusRecord::class),
        HealthPermission.getWritePermission(OvulationTestRecord::class),
        HealthPermission.getWritePermission(SexualActivityRecord::class)
    )
    println(reqs)
}

package de.coldtea.smplr.alarm

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import de.coldtea.smplr.alarm.databinding.FragmentMainBinding
import de.coldtea.smplr.alarm.extensions.nowPlus
import de.coldtea.smplr.alarm.lockscreenalarm.ActivityLockScreenAlarm
import de.coldtea.smplr.smplralarm.smplrAlarmCancel
import de.coldtea.smplr.smplralarm.smplrAlarmSet
import timber.log.Timber
import java.util.*

class MainFragment : Fragment() {

    lateinit var binding: FragmentMainBinding

    var requestCodeAlarm1 = -1
    var requestCodeAlarm2 = -1
    var requestCodeAlarm3 = -1
    var requestCodeAlarm4 = -1

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = FragmentMainBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.setAlarm1.setOnClickListener {
            val intent = Intent(
                requireContext().applicationContext,
                ActivityLockScreenAlarm::class.java
            )

            intent.putExtra("SmplrText", "You did it, you crazy bastard you did it!")

            val cal = Calendar.getInstance()
            requestCodeAlarm1 = createBasicNotificationWithFullScreenIntent(cal.nowPlus(1), intent)

        }

        binding.setAlarm2.setOnClickListener {
            val intent = Intent(
                requireContext().applicationContext,
                ActivityLockScreenAlarm::class.java
            )

            intent.putExtra("SmplrText", "You did it, you crazy bastard you did it!")


            val cal = Calendar.getInstance()
            requestCodeAlarm2 = createBasicNotificationWithFullScreenIntent(cal.nowPlus( 2), intent)

        }

        binding.setAlarm3.setOnClickListener {
            val intent = Intent(
                requireContext().applicationContext,
                ActivityLockScreenAlarm::class.java
            )

            intent.putExtra("SmplrText", "You did it, you crazy bastard you did it!")


            val cal = Calendar.getInstance()
            requestCodeAlarm3 = createBasicNotificationWithFullScreenIntent(cal.nowPlus(3), intent)

        }

        binding.setAlarm4.setOnClickListener {
            val intent = Intent(
                requireContext().applicationContext,
                ActivityLockScreenAlarm::class.java
            )

            intent.putExtra("SmplrText", "You did it, you crazy bastard you did it!")


            val cal = Calendar.getInstance()
            requestCodeAlarm4 = createBasicNotificationWithFullScreenIntent(cal.nowPlus(4), intent)

        }

        binding.cancelAlarm1.setOnClickListener {
            if(requestCodeAlarm1 != -1) cancelNotification(requestCodeAlarm1)
        }

        binding.cancelAlarm2.setOnClickListener {
            if(requestCodeAlarm2 != -1) cancelNotification(requestCodeAlarm2)
        }

        binding.cancelAlarm3.setOnClickListener {
            if(requestCodeAlarm3 != -1) cancelNotification(requestCodeAlarm3)
        }

        binding.cancelAlarm4.setOnClickListener {
            if(requestCodeAlarm4 != -1) cancelNotification(requestCodeAlarm4)
        }

    }

    private fun createBasicNotificationWithFullScreenIntent(timePair: Pair<Int,Int>, intent: Intent) = smplrAlarmSet(requireContext().applicationContext) {
        hour { timePair.first }
        min { timePair.second }
        fullScreenIntent {
            intent
        }
        onAlarmRings{
                alarmId -> Timber.i("SmplrAlarmApp.MainFragment.onAlarmRings: $alarmId")
        }
    }

    private fun cancelNotification(requestCode: Int) = smplrAlarmCancel(requireContext().applicationContext){
        requestCode { requestCode }
    }

}
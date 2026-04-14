package com.dna.app.ui.nav

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dna.app.data.work.LibrarySyncWorker
import com.dna.app.ui.auth.AuthViewModel
import com.dna.app.ui.auth.SignInScreen
import com.dna.app.ui.detail.DressDetailScreen
import com.dna.app.ui.library.LibraryScreen
import com.dna.app.ui.upload.UploadDressScreen

object Routes {
    const val SIGN_IN = "sign_in"
    const val LIBRARY = "library"
    const val UPLOAD = "upload"
    const val GENERATE = "generate"
    const val DETAIL = "detail/{dressId}"

    fun detail(dressId: String): String = "detail/$dressId"
}

@Composable
fun DnaNavHost(
    nav: NavHostController = rememberNavController(),
) {
    val authVm: AuthViewModel = hiltViewModel()
    val uid by authVm.currentUid.collectAsStateWithLifecycle()
    val context: Context = LocalContext.current

    LaunchedEffect(uid) {
        val current = nav.currentBackStackEntry?.destination?.route
        when {
            uid == null && current != Routes.SIGN_IN -> {
                nav.navigate(Routes.SIGN_IN) {
                    popUpTo(0) { inclusive = true }
                }
            }
            uid != null && current == Routes.SIGN_IN -> {
                nav.navigate(Routes.LIBRARY) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
        // Whenever we land on a signed-in session, kick a one-shot sync and
        // make sure the periodic reconcile is registered.
        if (uid != null) {
            LibrarySyncWorker.kickOnce(context)
            LibrarySyncWorker.schedulePeriodic(context)
        }
    }

    NavHost(
        navController = nav,
        startDestination = if (uid == null) Routes.SIGN_IN else Routes.LIBRARY,
    ) {
        composable(Routes.SIGN_IN) { SignInScreen(viewModel = authVm) }

        composable(Routes.LIBRARY) {
            LibraryScreen(
                onAddDress = { nav.navigate(Routes.UPLOAD) },
                onGenerate = { /* TODO(M5): nav.navigate(Routes.GENERATE) */ },
                onDressClick = { nav.navigate(Routes.detail(it.id)) },
            )
        }

        composable(Routes.UPLOAD) {
            UploadDressScreen(
                onBack = { nav.popBackStack() },
                onUploaded = { nav.popBackStack() },
            )
        }

        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("dressId") { type = NavType.StringType }),
        ) {
            DressDetailScreen(
                onBack = { nav.popBackStack() },
                onDeleted = { nav.popBackStack() },
            )
        }
    }
}

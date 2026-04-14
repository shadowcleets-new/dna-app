package com.dna.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dna.app.ui.auth.AuthViewModel
import com.dna.app.ui.auth.SignInScreen
import com.dna.app.ui.library.LibraryScreen
import com.dna.app.ui.upload.UploadDressScreen

object Routes {
    const val SIGN_IN = "sign_in"
    const val LIBRARY = "library"
    // M3+:
    const val UPLOAD = "upload"
    const val GENERATE = "generate"
    const val DETAIL = "detail/{dressId}"
}

@Composable
fun DnaNavHost(
    nav: NavHostController = rememberNavController(),
) {
    // Auth state is the routing switch. We hoist one AuthViewModel at the NavHost
    // level so both screens observe the same StateFlow.
    val authVm: AuthViewModel = hiltViewModel()
    val uid by authVm.currentUid.collectAsStateWithLifecycle()

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
                onDressClick = { /* TODO(M4): nav.navigate(detailRoute(it.id)) */ },
            )
        }

        composable(Routes.UPLOAD) {
            UploadDressScreen(
                onBack = { nav.popBackStack() },
                onUploaded = { nav.popBackStack() },
            )
        }
    }
}

/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2016 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package playground.agarwalamit.opdyts.patna.networkModesOnly;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import floetteroed.opdyts.DecisionVariableRandomizer;
import floetteroed.opdyts.ObjectiveFunction;
import floetteroed.opdyts.convergencecriteria.ConvergenceCriterion;
import floetteroed.opdyts.convergencecriteria.FixedIterationNumberConvergenceCriterion;
import floetteroed.opdyts.searchalgorithms.RandomSearch;
import floetteroed.opdyts.searchalgorithms.SelfTuner;
import opdytsintegration.MATSimSimulator2;
import opdytsintegration.MATSimStateFactoryImpl;
import opdytsintegration.utils.TimeDiscretization;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.VspExperimentalConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;
import org.matsim.core.controler.events.ShutdownEvent;
import org.matsim.core.controler.listener.ShutdownListener;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.scoring.functions.ScoringParametersForPerson;
import org.matsim.core.utils.io.IOUtils;
import playground.agarwalamit.analysis.modalShare.ModalShareControlerListener;
import playground.agarwalamit.analysis.modalShare.ModalShareEventHandler;
import playground.agarwalamit.analysis.tripTime.ModalTravelTimeControlerListener;
import playground.agarwalamit.analysis.tripTime.ModalTripTravelTimeHandler;
import playground.agarwalamit.opdyts.*;
import playground.agarwalamit.opdyts.analysis.DecisionVariableAndBestSolutionPlotter;
import playground.agarwalamit.opdyts.analysis.OpdytsConvergencePlotter;
import playground.agarwalamit.opdyts.analysis.OpdytsModalStatsControlerListener;
import playground.agarwalamit.utils.FileUtils;
import playground.kai.usecases.opdytsintegration.modechoice.EveryIterationScoringParameters;

/**
 * @author amit
 */

public class PatnaNetworkModesCalibrator {

	private static final OpdytsScenario PATNA_1_PCT = OpdytsScenario.PATNA_1Pct;
	private static boolean isPlansRelaxed = false;

	public static void main(String[] args) {
		String configFile;
		Config config = ConfigUtils.createConfig();
		OpdytsConfigGroup opdytsConfigGroup = ConfigUtils.addOrGetModule(config, OpdytsConfigGroup.GROUP_NAME,OpdytsConfigGroup.class);
		String OUT_DIR = null;

		if ( args.length>0 ) {
			configFile = args[0];
			OUT_DIR = args[1];

			opdytsConfigGroup.setVariationSizeOfRamdomizeDecisionVariable(Double.valueOf(args[2]));
			opdytsConfigGroup.setNumberOfIterationsForConvergence(Integer.valueOf(args[3]));
			opdytsConfigGroup.setNumberOfIterationsForAveraging(Integer.valueOf(args[4]));
			opdytsConfigGroup.setSelfTuningWeight(Double.valueOf(args[5]));
			opdytsConfigGroup.setPopulationSize(Integer.valueOf(args[6]));

			isPlansRelaxed = Boolean.valueOf(args[7]);;
		} else {
			configFile = FileUtils.RUNS_SVN+"/opdyts/patna/input_networkModes/"+"/config_networkModesOnly.xml";
			OUT_DIR = FileUtils.RUNS_SVN+"/opdyts/patna/output_networkModes/";
		}

		String relaxedPlansDir = OUT_DIR+"/initialPlans2RelaxedPlans/";
		if (! isPlansRelaxed ) {
			// relax the plans first.
			PatnaNetworkModesPlansRelaxor relaxor = new PatnaNetworkModesPlansRelaxor();
			relaxor.run(new String[]{configFile, relaxedPlansDir});
		}

		OUT_DIR = OUT_DIR+"/calibration_variationSize"+opdytsConfigGroup.getVariationSizeOfRamdomizeDecisionVariable()+"_AvgIts"+opdytsConfigGroup.getNumberOfIterationsForAveraging()+"/";

		ConfigUtils.loadConfig(config,configFile);
		config.setContext(IOUtils.getUrlFromFileOrResource(configFile));
		config.plans().setInputFile(relaxedPlansDir+"/output_plans.xml.gz");

		config.vspExperimental().setVspDefaultsCheckingLevel(VspExperimentalConfigGroup.VspDefaultsCheckingLevel.warn); // must be warn, since opdyts override few things

		config.controler().setOutputDirectory(OUT_DIR);
		opdytsConfigGroup.setOutputDirectory(OUT_DIR);

		Scenario scenario = ScenarioUtils.loadScenario(config);
		scenario.getConfig().controler().setOverwriteFileSetting(OverwriteFileSetting.deleteDirectoryIfExists);

		// == opdyts settings
		// this is something like time bin generator
		TimeDiscretization timeDiscretization = new TimeDiscretization(opdytsConfigGroup.getStartTime(), opdytsConfigGroup.getBinSize(), opdytsConfigGroup.getBinCount());

		List<String> modes2consider = Arrays.asList("car","bike","motorbike");
		DistanceDistribution referenceStudyDistri = new PatnaNetworkModesOneBinDistanceDistribution(PATNA_1_PCT);
		OpdytsModalStatsControlerListener stasControlerListner = new OpdytsModalStatsControlerListener(modes2consider,referenceStudyDistri);

		// following is the  entry point to start a matsim controler together with opdyts
		MATSimSimulator2<ModeChoiceDecisionVariable> simulator = new MATSimSimulator2<>(new MATSimStateFactoryImpl<>(), scenario, timeDiscretization, new HashSet<>(modes2consider));
		simulator.addOverridingModule(new AbstractModule() {

			@Override
			public void install() {
//				addControlerListenerBinding().to(KaiAnalysisListener.class);
				addControlerListenerBinding().toInstance(stasControlerListner);

				this.bind(ModalShareEventHandler.class);
				this.addControlerListenerBinding().to(ModalShareControlerListener.class);

				this.bind(ModalTripTravelTimeHandler.class);
				this.addControlerListenerBinding().to(ModalTravelTimeControlerListener.class);

				bind(ScoringParametersForPerson.class).to(EveryIterationScoringParameters.class);

				addControlerListenerBinding().toInstance(new ShutdownListener() {
					@Override
					public void notifyShutdown(ShutdownEvent event) {
						// remove the unused iterations
						String dir2remove = event.getServices().getControlerIO().getOutputPath()+"/ITERS/";
						IOUtils.deleteDirectoryRecursively(new File(dir2remove).toPath());
					}
				});
			}
		});

		// this is the objective Function which returns the value for given SimulatorState
		// in my case, this will be the distance based modal split
		ObjectiveFunction objectiveFunction = new ModeChoiceObjectiveFunction(referenceStudyDistri);

		//search algorithm
		// randomize the decision variables (for e.g.\ utility parameters for modes)
		DecisionVariableRandomizer<ModeChoiceDecisionVariable> decisionVariableRandomizer = new ModeChoiceRandomizer(scenario,
				RandomizedUtilityParametersChoser.ONLY_ASC, PATNA_1_PCT, null, modes2consider);

		// what would be the decision variables to optimize the objective function.
		ModeChoiceDecisionVariable initialDecisionVariable = new ModeChoiceDecisionVariable(scenario.getConfig().planCalcScore(),scenario, modes2consider, PATNA_1_PCT);

		// what would decide the convergence of the objective function
		ConvergenceCriterion convergenceCriterion = new FixedIterationNumberConvergenceCriterion(opdytsConfigGroup.getNumberOfIterationsForConvergence(),opdytsConfigGroup.getNumberOfIterationsForAveraging());

		RandomSearch<ModeChoiceDecisionVariable> randomSearch = new RandomSearch<>(
				simulator,
				decisionVariableRandomizer,
				initialDecisionVariable,
				convergenceCriterion,
				opdytsConfigGroup.getMaxIteration(), // this many times simulator.run(...) and thus controler.run() will be called.
				opdytsConfigGroup.getMaxTransition(),
				opdytsConfigGroup.getPopulationSize(),
				MatsimRandom.getRandom(),
				opdytsConfigGroup.isInterpolate(),
				objectiveFunction,
				opdytsConfigGroup.isIncludeCurrentBest()
		);

		// probably, an object which decide about the inertia
		SelfTuner selfTuner = new SelfTuner(opdytsConfigGroup.getInertia());
		selfTuner.setNoisySystem(true);
		randomSearch.setLogPath(opdytsConfigGroup.getOutputDirectory());

		// run it, this will eventually call simulator.run() and thus controler.run
		randomSearch.run(selfTuner );

		OpdytsConvergencePlotter opdytsConvergencePlotter = new OpdytsConvergencePlotter();
		opdytsConvergencePlotter.readFile(OUT_DIR+"/opdyts.con");
		opdytsConvergencePlotter.plotData(OUT_DIR+"/convergence.png");

		DecisionVariableAndBestSolutionPlotter decisionVariableAndBestSolutionPlotter = new DecisionVariableAndBestSolutionPlotter("bicycle");
		decisionVariableAndBestSolutionPlotter.readFile(OUT_DIR+"/opdyts.log");
		decisionVariableAndBestSolutionPlotter.plotData(OUT_DIR+"/decisionVariableVsASC.png");
	}
}
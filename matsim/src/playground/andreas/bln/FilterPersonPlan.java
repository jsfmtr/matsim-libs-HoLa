package playground.andreas.bln;

import org.matsim.api.basic.v01.BasicScenarioImpl;
import org.matsim.api.basic.v01.TransportMode;
import org.matsim.core.api.population.Leg;
import org.matsim.core.api.population.Person;
import org.matsim.core.api.population.Plan;
import org.matsim.core.api.population.PlanElement;
import org.matsim.core.api.population.Population;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.network.MatsimNetworkReader;
import org.matsim.core.network.NetworkLayer;
import org.matsim.core.population.MatsimPopulationReader;
import org.matsim.core.population.PopulationImpl;
import org.matsim.core.population.PopulationReader;

/**
 * Filter persons, not using a specific TransportMode.
 * 
 * @author aneumann
 * 
 */
public class FilterPersonPlan extends NewPopulation {
	private int planswritten = 0;
	private int personshandled = 0;

	public FilterPersonPlan(Population plans, String filename) {
		super(plans, filename);
	}

	@Override
	public void run(Person person) {
		
		this.personshandled++;
		
		if(person.getPlans().size() != 1){
			System.err.println("Person got more than one plan");
		} else {
			
			Plan plan = person.getPlans().get(0);
			boolean keepPlan = true;
			
			// only keep person if every leg is a car leg
			for (PlanElement planElement : plan.getPlanElements()) {
				if(planElement instanceof Leg){
					if(((Leg)planElement).getMode() != TransportMode.car){
						keepPlan = false;
					}
				}
			}
			
			if(keepPlan){
				this.popWriter.writePerson(person);
				this.planswritten++;
			}
			
		}

	}
	
	public static void main(final String[] args) {
		Gbl.startMeasurement();
		
		BasicScenarioImpl sc = new BasicScenarioImpl();
		Gbl.setConfig(sc.getConfig());

		String networkFile = "./poa4.xml.gz";
		String inPlansFile = "./poa.xml.gz";
		String outPlansFile = "./poa.car.xml.gz";

		NetworkLayer net = new NetworkLayer();
		new MatsimNetworkReader(net).readFile(networkFile);

		Population inPop = new PopulationImpl();
		PopulationReader popReader = new MatsimPopulationReader(inPop, net);
		popReader.readFile(inPlansFile);

		FilterPersonPlan dp = new FilterPersonPlan(inPop, outPlansFile);
		dp.run(inPop);
		System.out.println(dp.personshandled + " persons handled; " + dp.planswritten + " plans written to file");
		dp.writeEndPlans();

		Gbl.printElapsedTime();
	}
}
